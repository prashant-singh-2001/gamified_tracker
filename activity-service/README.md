# Activity Service

**Manages activity definitions and logs activity sessions, awarding XP.** · Port **8081**

Owns the catalog of activities (Study, Gaming, Work, …) and the record of each logged session. When a session is logged it computes duration and XP (with an occasional random bonus), saves the log, and — since issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16) — publishes an `ActivityLogged` domain event so the Gamification Service can apply the XP **asynchronously**. This service is now a pure event **producer**: it has no Feign client and makes no synchronous call to gamification-service at all. Full design: [`EVENT_DRIVEN_DECOUPLING.md`](../docs/features/event-driven-decoupling.md).

## Role in the system

```
  api-gateway ──HTTP──►  activity-service (8081)          gamification-service (8082)
    (8080)                    │                                     ▲
                             ▼                                     │ @RabbitListener
                       PostgreSQL (5433)                           │ (idempotent consumer)
                       activity, activity_log,                     │
                       outbox_event                                │
                             │                                     │
                             └──── OutboxRelay (@Scheduled) ──► RabbitMQ ──┘
                                   publishes ActivityLoggedEvent   (activity.events
                                   from outbox_event rows           exchange)
```

Called by: **api-gateway**. Publishes to: **RabbitMQ** (`ActivityLoggedEvent`, consumed by gamification-service — no direct HTTP/Feign call). Registers with **Eureka**; persists to **PostgreSQL**.

## Responsibilities

- CRUD-ish management of `Activity` definitions (name, category, XP multiplier, active flag).
- Record `ActivityLog` sessions (user, activity, start/end time, notes).
- Compute `durationMinutes` and `xpEarned` (base × multiplier × optional random bonus).
- **Transactional Outbox**: save the log and write an `outbox_event` row in one transaction, then relay it to RabbitMQ so the Gamification Service can apply the XP — decoupled from this request's success/failure.

## Tech stack

- Java 17, Spring Boot 3.5, Spring Cloud 2025 (Eureka client)
- **Spring AMQP** (`spring-boot-starter-amqp`) — RabbitMQ producer (`RabbitTemplate` + `Jackson2JsonMessageConverter`) and the `@Scheduled` outbox relay (`@EnableScheduling`)
- Spring Data JPA + PostgreSQL
- Java 17 idioms: **records** for DTOs, `switch` expression on `Category`, `java.util.concurrent.ThreadLocalRandom` for the XP-bonus roll
- Interface + `impl/` service layout (`ActivityService`/`ActivityServiceImpl`, `ActivityLogService`/`ActivityLogServiceImpl`)
- Entry point: `ActivityServiceApplication` (`@SpringBootApplication` + `@EnableScheduling`; **no** `@EnableFeignClients` — Feign was removed)

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
| `xpMultiplier` | double | **optional override** — `≤ 0` or omitted falls back to the `Category` base (see [XP multiplier resolution](#xp-multiplier-resolution-issue-10)); a positive value overrides it. e.g. `1.5` |
| `active` | boolean | soft enable/disable |
| `description` | String | optional |
| `createdAt` | ISO-8601 | **ignored** — server sets current time |

Response `200 OK` — `ActivityResponseRecord` (`name`, `category`, `xpMultiplier`, `active`, `description`, `createdAt`). The `xpMultiplier` returned is the **effective** value (override, or resolved category base), never a misleading `0.0`.

### Activity Logs — `/activitylog`

#### `GET /activitylog/{id}`
Fetch one log by numeric id. `200 OK` → `ActivityLogResponse`, or `404 Not Found` → `ProblemDetail`.

#### `POST /activitylog/`
Record a session, compute XP, save it, and publish an `ActivityLogged` event (async — see below) instead of calling gamification synchronously. Requires a `userId` request header (Long, **required** — a missing header is rejected before the service layer even runs). Through the Gateway this header is injected from the caller's JWT and can't be spoofed; hit directly on `:8081` it's caller-supplied, since this service has no security layer of its own.

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
| `leveledUp` | boolean | **Always `false` on this response.** XP is applied asynchronously by gamification-service's RabbitMQ consumer, so this endpoint can't know yet whether the session leveled the user up — check `GET /level/user/{id}` on gamification-service (or `GET /api/level/user/{id}` via the Gateway) shortly after |

- `404 Not Found` → `ProblemDetail` if `activityName` doesn't exist.

#### `GET /activitylog/user/{id}`
All logs for a user. → `200 OK`, array of `ActivityLogResponse`.

> `GET /activitylog/{id}` and `GET /activitylog/user/{id}` always return `bonusApplied: false`, `bonusMultiplier: 1.0`, `leveledUp: false` — `bonusApplied`/`bonusMultiplier` aren't persisted columns, only computed in-memory on the `POST` that created the log; `leveledUp` is `false` everywhere now, including on the `POST` response itself (see above).

## Data model

**`activity`**:
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK |
| `name` | String | unique |
| `category` | enum (STRING) | `Category` |
| `xp_multiplier` | double | per-activity override; `≤ 0` means "no override, use the category base" (see [XP multiplier resolution](#xp-multiplier-resolution-issue-10)) |
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

`Category` enum: `STUDY, WORK, GAMING, CHORES, HEALTH, OTHER` — also carries a `baseXpMultiplier()` switch expression (`STUDY`/`WORK` 1.5, `HEALTH` 1.3, `OTHER` 1.0, `CHORES` 0.8, `GAMING` 0.5), which is the default when an activity has no per-activity `xpMultiplier` override.

### XP multiplier resolution (issue #10)

`Activity.effectiveXpMultiplier()` is the single source of truth for the multiplier used in XP math:

- If the activity's stored `xpMultiplier > 0`, that per-activity value is the **override** and wins.
- Otherwise (`≤ 0`, including the `0.0` a client gets by omitting the field), it falls back to
  `category.baseXpMultiplier()` — or `OTHER` (1.0) if the category is somehow null.

Resolution happens **at log time** (in `ActivityLogServiceImpl`), not at create time, so an activity
with no override always tracks its category's current base. This also closes the latent bug where an
activity created without a multiplier earned `0` XP forever, and makes a negative multiplier degrade
to the category base instead of producing negative XP.

**`outbox_event`** — Transactional Outbox table (new in #16):
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK, identity |
| `aggregate_type` | String | `"ActivityLog"` |
| `aggregate_id` | bigint | = the `activity_log.id` this event describes |
| `event_type` | String | `"ActivityLogged"` |
| `payload` | text | JSON-serialized `ActivityLoggedEvent(logId, userId, activityId, xpEarned)` |
| `idempotency_key` | String, unique | = `aggregate_id` as a string; the consumer's dedup key |
| `created_at` | timestamp | |
| `published_at` | timestamp, nullable | `null` until `OutboxRelay` successfully publishes it to RabbitMQ |

## Key internal flow — `ActivityLogServiceImpl.addActivityLogResponseResponseEntity()`

Now `@Transactional` (fixes [issue #4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4) — the log used to be at risk of being lost if the old synchronous gamification call failed):

1. Resolve the `Activity` by name (`404` if missing) and build the `ActivityLog`, with `userId` taken from the `userId` request header (not the request body).
2. `durationMinutes = Duration.between(startTime, endTime).toMinutes()`.
3. **XP bonus roll:** `ThreadLocalRandom.current()` — 20% chance of a `1.1–1.5×` bonus, else `1.0×`. `xpEarned = durationMinutes × activity.effectiveXpMultiplier() × bonus` — the effective multiplier is the per-activity override or the category base ([issue #10](https://github.com/prashant-singh-2001/gamified_tracker/issues/10); see [XP multiplier resolution](#xp-multiplier-resolution-issue-10)). `bonusApplied`/`bonusMultiplier` are captured from this roll for the response.
4. **Save the `ActivityLog` FIRST** — the generated id becomes the event's `logId` / the outbox row's `idempotency_key`.
5. **Same transaction:** build an `ActivityLoggedEvent(logId, userId, activityId, xpEarned)`, serialize it to JSON, and save an `OutboxEvent` row (`published_at: null`). No Feign call, no synchronous dependency on gamification-service being up.
6. Return `ActivityLogResponse` with real `bonusApplied`/`bonusMultiplier`, but **`leveledUp` is always `false`** — it's now eventual (see `outbox/OutboxRelay.java` + gamification-service's `ActivityLoggedListener` for how it eventually gets applied).

Full design + code walkthrough: [`EVENT_DRIVEN_DECOUPLING.md`](../docs/features/event-driven-decoupling.md).

## Configuration

Standard env vars (root [`.env.example`](../.env.example)): `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, `server.port=8081`, `eureka.client.service-url.defaultZone`, `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD`, `outbox.relay.delay-ms` (default `2000`).

## Inter-service dependencies

- **Publishes to:** RabbitMQ (`ActivityLoggedEvent`, exchange `activity.events`) — consumed by gamification-service. No Feign client, no synchronous HTTP call to any other service.
- **Called by:** api-gateway.
- **Infra:** Eureka, PostgreSQL, RabbitMQ.

## Running

```bash
docker-compose up --build          # whole stack, from repo root — includes RabbitMQ
# or standalone (needs Postgres + Eureka + RabbitMQ reachable):
cd activity-service && mvn spring-boot:run
```

## Testing

```bash
mvn test          # from activity-service/
```

Includes `@WebMvcTest` controller tests, `@DataJpaTest` repository tests (`ActivityRepositoryTest`, `OutboxEventRepositoryTest`), and Mockito service/component tests (`ActivityServiceImplTest`, `ActivityLogServiceImplTest`, `OutboxRelayTest`) — the outbox producer and the polling publisher are both covered without needing a real broker (mocked `OutboxEventRepository`/`RabbitTemplate`).

## Troubleshooting

- **`activityName` not found → 404** — the activity must be created via `POST /activity/` first.
- **`400` on `POST /activitylog/`** — the `userId` header is required; a request without it is rejected before the service layer runs. The Gateway supplies it automatically; a direct call to `:8081` must set it manually.
- **XP never shows up on gamification-service** — check the RabbitMQ management UI (`http://localhost:15672`, guest/guest) for a stuck/DLQ'd message, and confirm `outbox_event.published_at` is actually getting stamped (the `OutboxRelay` polls every `outbox.relay.delay-ms`, default 2s).
- **Health check:** `curl http://localhost:8081/actuator/health` — `spring-boot-starter-actuator` is wired (exposes `health`, `info`; Docker healthcheck depends on this).

## Related docs

- [Root README](../README.md) · [API.md](../API.md) · [gamification-service README](../gamification-service/README.md) · [EVENT_DRIVEN_DECOUPLING.md](../docs/features/event-driven-decoupling.md)
