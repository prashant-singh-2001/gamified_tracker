# Activity Service

**Manages activity definitions and logs activity sessions, awarding XP.** · Port **8081**

Owns the catalog of activities (Study, Gaming, Work, …) and the record of each logged session. When a session is logged it computes duration and XP (with an occasional random bonus) and forwards the earned XP to the Gamification Service over Feign.

## Role in the system

```
  api-gateway ──Feign──►  activity-service (8081)  ──Feign──►  gamification-service (8082)
    (8080)                    │       POST /level (XP earned)
                             ▼
                       PostgreSQL (5433)
                       activity, activity_log
```

Called by: **api-gateway**. Calls: **gamification-service** (`POST /level`) after each activity log. Registers with **Eureka**; persists to **PostgreSQL**.

## Responsibilities

- CRUD-ish management of `Activity` definitions (name, category, XP multiplier, active flag).
- Record `ActivityLog` sessions (user, activity, start/end time, notes).
- Compute `durationMinutes` and `xpEarned` (base × multiplier × optional random bonus).
- Push earned XP to the Gamification Service so levels update.

## Tech stack

- Java 17, Spring Boot 3.5, Spring Cloud 2025 (Eureka client, **OpenFeign**)
- Spring Data JPA + PostgreSQL
- Java 17 idioms: **records** for DTOs, `switch` expression on `Category`, `java.util.random.RandomGenerator`
- Interface + `impl/` service layout (`ActivityService`/`ActivityServiceImpl`, `ActivityLogService`/`ActivityLogServiceImpl`)
- Entry point: `ActivityServiceApplication` (`@SpringBootApplication` + `@EnableFeignClients`)

## API reference

Base paths `/activity` and `/activitylog`. No auth at this layer (enforced at the gateway). JSON bodies.

### Activities — `/activity`

#### `GET /activity/`
List all activities. → `200 OK`, array of `ActivityResponseRecord`.

#### `GET /activity/{name}`
Fetch one activity by name.
- `200 OK` → `ActivityResponseRecord`
- `404 Not Found` → `ProblemDetail` (`"Activity not found: {name}"`)

#### `POST /activity/`
Create an activity.

Request — `ActivityRequestRecord`:
| Field | Type | Notes |
|-------|------|-------|
| `name` | String | should be unique (DB constraint) |
| `category` | enum | `STUDY` \| `WORK` \| `GAMING` \| `CHORES` \| `HEALTH` \| `OTHER` |
| `xpMultiplier` | double | e.g. `1.5` |
| `active` | boolean | soft enable/disable |
| `description` | String | optional |
| `createdAt` | ISO-8601 | **ignored** — server sets current time |

Response `200 OK` — `ActivityResponseRecord` (`name`, `category`, `xpMultiplier`, `active`, `description`, `createdAt`).

### Activity Logs — `/activitylog`

#### `GET /activitylog/{id}`
Fetch one log by numeric id. `200 OK` → `ActivityLogResponse`, or `404 Not Found` → `ProblemDetail`.

#### `POST /activitylog/`
Record a session, compute XP, and notify gamification. Requires a `userId` request header (Long, **required** — a missing header is rejected before the service layer even runs). Through the Gateway this header is injected from the caller's JWT and can't be spoofed; hit directly on `:8081` it's caller-supplied, since this service has no security layer of its own.

Request — `ActivityLogRequest` (no `userId` field — see header above):
| Field | Type | Notes |
|-------|------|-------|
| `activityName` | String | must match an existing `Activity.name`, else `404` |
| `startTime` | ISO-8601 | |
| `endTime` | ISO-8601 | should be after `startTime` |
| `notes` | String | optional |
| `createdAt` | ISO-8601 | **ignored** — server sets current time |

Response `200 OK` — `ActivityLogResponse`:
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long | |
| `userId` | Long | |
| `activity` | object | the full `Activity` |
| `startTime` / `endTime` | ISO-8601 | |
| `durationMinutes` | Long | `endTime − startTime` |
| `xpEarned` | double | `durationMinutes × activity.xpMultiplier × bonus` |
| `notes` | String | |
| `createdAt` | ISO-8601 | |
| `bonusApplied` | boolean | `true` if the XP-bonus roll succeeded for this session |
| `bonusMultiplier` | double | the multiplier actually used — `1.0` if no bonus, else the rolled `[1.1, 1.5)` value |
| `leveledUp` | boolean | `true` if this XP push crossed a level threshold, per the Gamification Service's response |

- `404 Not Found` → `ProblemDetail` if `activityName` doesn't exist.

#### `GET /activitylog/user/{id}`
All logs for a user. → `200 OK`, array of `ActivityLogResponse`.

> `GET /activitylog/{id}` and `GET /activitylog/user/{id}` always return `bonusApplied: false`, `bonusMultiplier: 1.0`, `leveledUp: false` — these three fields aren't persisted columns, only computed in-memory on the `POST` that created the log, so historical reads can't recover the real values.

## Data model

**`activity`**:
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK |
| `name` | String | unique |
| `category` | enum (STRING) | `Category` |
| `xp_multiplier` | double | |
| `active` | boolean | soft-delete flag |
| `description` | String | |
| `created_at` | timestamp | |

**`activity_log`**:
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK |
| `user_id` | bigint | from the trusted `userId` request header (gateway-injected, not client-body-supplied — see API reference above) |
| `activity` | FK → `activity` | `@ManyToOne` |
| `start_time` / `end_time` | timestamp | |
| `duration_minutes` | bigint | computed |
| `xp_earned` | double | computed |
| `notes` | String | |
| `created_at` | timestamp | |

`Category` enum: `STUDY, WORK, GAMING, CHORES, HEALTH, OTHER` — also carries a `baseXpMultiplier()` switch expression.

## Key internal flow — `ActivityLogServiceImpl.addActivityLogResponseResponseEntity()`

1. Resolve the `Activity` by name (`404` if missing) and build the `ActivityLog`, with `userId` taken from the `userId` request header (not the request body).
2. `durationMinutes = Duration.between(startTime, endTime).toMinutes()`.
3. **XP bonus roll:** `RandomGenerator.getDefault()` — 20% chance of a `1.1–1.5×` bonus, else `1.0×`. `xpEarned = durationMinutes × activity.xpMultiplier × bonus`. `bonusApplied`/`bonusMultiplier` are captured from this roll for the response.
4. **Feign call** `gamificationClient.createLevelTracker(userId, new LevelTrackerRequestDTO(activityId, xpEarned))` — `userId` goes as an explicit Feign header parameter, not in the body — → gamification `POST /level`. The response's `leveledUp` flag is read back and threaded into this endpoint's own response.
5. Save the log and return `ActivityLogResponse`, including `bonusApplied`, `bonusMultiplier`, and `leveledUp`.

> ⚠️ Known ordering issue: the Feign call currently happens **before** the local save — if gamification is down, the log isn't persisted. Tracked in [issue #4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4).

## Configuration

Standard env vars (root [`.env.example`](../.env.example)): `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, `server.port=8081`, `eureka.client.service-url.defaultZone`.

## Inter-service dependencies

- **Calls:** gamification-service via `GamificationClient` (`@FeignClient(name = "gamification-service")` → `POST /level`).
- **Called by:** api-gateway.
- **Infra:** Eureka, PostgreSQL.

## Running

```bash
docker-compose up --build          # whole stack, from repo root
# or standalone (needs Postgres + Eureka; gamification for the Feign call):
cd activity-service && mvn spring-boot:run
```

## Testing

```bash
mvn test          # from activity-service/
```

Includes `@WebMvcTest` controller tests, a `@DataJpaTest` repository test, and Mockito service tests (`ActivityServiceImplTest`, `ActivityLogServiceImplTest`).

## Troubleshooting

- **`POST /activitylog/` fails when gamification is down** — see the ordering issue above ([#4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4)).
- **`activityName` not found → 404** — the activity must be created via `POST /activity/` first.
- **`400` on `POST /activitylog/`** — the `userId` header is required; a request without it is rejected before the service layer runs. The Gateway supplies it automatically; a direct call to `:8081` must set it manually.
- **Health check:** `curl http://localhost:8081/actuator/health` — `spring-boot-starter-actuator` is wired (exposes `health`, `info`; Docker healthcheck depends on this).

## Related docs

- [Root README](../README.md) · [API.md](../API.md) · [gamification-service README](../gamification-service/README.md)
