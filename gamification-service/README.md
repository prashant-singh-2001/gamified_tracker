# Gamification Service

**Tracks XP, levels, and level thresholds per user + activity.** · Port **8082**

The gamification engine. When a user logs an activity, activity-service publishes an `ActivityLogged` event to RabbitMQ instead of calling this service directly (issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)); this service consumes it **idempotently**, accumulates XP, recomputes the user's level for that activity against configurable thresholds, and keeps an append-only history of every change. It is a **leaf** service for outbound calls — it has no Feign clients and calls no other service — but it is now also a RabbitMQ **consumer**. Full design: [`EVENT_DRIVEN_DECOUPLING.md`](../docs/features/event-driven-decoupling.md).

## Role in the system

```
  activity-service ──RabbitMQ──►  gamification-service (8082)
     (8081, outbox)   ActivityLogged   │  @RabbitListener → dedup (processed_event)
                       event           │  → LevelTrackerServiceImpl.save(userId, dto)
                                       ▼
                                PostgreSQL (5433)
                                level_tracker
                                level_tracker_archive
                                activity_level_threshold
                                processed_event

  (POST /level is still directly reachable via api-gateway / :8082 — a second, synchronous caller)
```

Called by: **activity-service** (async, via RabbitMQ, after each activity log) and directly via `POST /level` (through api-gateway or `:8082`). Registers with **Eureka** (8761); persists to **PostgreSQL**; consumes from **RabbitMQ**.

## Responsibilities

- Accumulate `totalXp` per `(userId, activityId)` pair, safely under concurrency.
- Recompute `level` and `currentLevelXp` from the highest reached `ActivityLevelThreshold`.
- Append a **previous-state** snapshot to an archive table on every update (audit/history).
- Manage the per-activity level thresholds (the XP curve).
- **Idempotently consume** `ActivityLogged` events from RabbitMQ — apply XP exactly once even under at-least-once redelivery.

## Tech stack

- Java 17, Spring Boot 3.5, Spring Cloud 2025 (Eureka client)
- **Spring AMQP** (`spring-boot-starter-amqp`) — `@RabbitListener` consumer, DLQ topology, listener retry (`config/RabbitConfig.java`)
- Spring Data JPA + PostgreSQL; **pessimistic row locking** for the XP accumulation
- Java 17 **sealed interface** (`LevelOutcome`) with pattern matching for the level decision
- Entry point: `GamificationServiceApplication` (`@SpringBootApplication`; no `@EnableFeignClients` — no service, sync or async, is called *from* here)

## API reference

Base paths: `/level` and `/threshold`. No auth at this layer (enforced only at the gateway). All bodies are JSON.

### Level Tracker — `/level`

#### `GET /level`
List every level-tracker row (all users, all activities). → `200 OK`, array of `LevelTrackerDto`.

#### `GET /level/{id}`
Fetch one row by its numeric surrogate id.
- `200 OK` → `LevelTrackerDto`
- `404 Not Found` → `ProblemDetail` (`"LevelTracker with id: {id} not found"`)

#### `POST /level`
Create-or-update XP for a `(userId, activityId)` pair and recompute level. **Two callers now** (since [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)): this HTTP endpoint — directly reachable through the Gateway at `/api/level` or `:8082` — and the `ActivityLoggedListener` RabbitMQ consumer, which calls `LevelTrackerServiceImpl.save(userId, dto)` **in-process**, bypassing this controller and the header entirely (its `userId` comes from the event payload, and it's deduped against `processed_event` before `save` runs). Requires a `userId` request header (Long, **required** — missing it is rejected before the service layer runs) **only for the HTTP path**; through the Gateway this is a trusted, JWT-derived value, but a direct call to `:8082` must supply it manually since this service has no security layer of its own.

Request — `LevelTrackerRequestDTO` (no `userId` field — see header above):
| Field | Type | Notes |
|-------|------|-------|
| `activityId` | Long | |
| `xp` | double | XP to **add**. Must be `>= 0` — a compact-constructor guard rejects negatives |

Response `200 OK` — `LevelTrackerDto`:
| Field | Type | Notes |
|-------|------|-------|
| `userId` | Long | |
| `activityId` | Long | |
| `level` | Integer | recomputed |
| `totalXp` | double | accumulated total after this call |
| `currentLevelXp` | double | XP within the current level (`totalXp − threshold.xpRequired`) |
| `leveledUp` | boolean | `true` only if **this specific call** crossed a threshold. Every `GET` endpoint below hardcodes this to `false`, regardless of the row's actual level — it reflects the outcome of a write, not stored state |

- `400 Bad Request` → `ProblemDetail` (`"Invalid request body"`) if `xp` is negative.

#### `GET /level/user/{userId}`
All level-tracker rows for a user (one per activity they've logged). → `200 OK`, array.

#### `GET /level/activity/{activityId}`
All level-tracker rows for an activity (one per user). → `200 OK`, array.

### Activity Level Threshold — `/threshold`

Defines the XP required to reach each level, per activity. **Thresholds must be seeded** (there is no default curve yet — see Troubleshooting).

#### `GET /threshold`
List all thresholds. → `200 OK`, array of `ActivityLevelThresholdDto`.

#### `POST /threshold`
Create (or overwrite) a threshold.

Request / Response — `ActivityLevelThresholdDto`:
| Field | Type |
|-------|------|
| `activityId` | Long |
| `level` | Integer |
| `xpRequired` | double |

#### `POST /threshold/activity`
Look up a single threshold by composite key — despite the `POST`, this is a **read** (the body is used only as a key; `xpRequired` is ignored).
- `200 OK` → the matching `ActivityLevelThresholdDto`
- `404 Not Found` → `ProblemDetail` (`"ActivityLevelThreshold not found"`)

## Data model

**`level_tracker`** — current state per user+activity:
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK, identity |
| `user_id` | bigint | part of unique key |
| `activity_id` | bigint | part of unique key |
| `level` | int | |
| `total_xp` | double | |
| `current_level_xp` | double | |

Unique constraint **`uk_level_tracker_user_activity`** on `(user_id, activity_id)` — one row per pair; also the conflict target for the atomic insert.

**`level_tracker_archive`** — append-only history (surrogate PK so rows accumulate, not overwrite):
| Column | Type | Notes |
|--------|------|-------|
| `id` | bigint | PK, identity |
| `user_id`, `activity_id` | bigint | plain columns (not a key) |
| `level`, `total_xp`, `current_level_xp` | — | snapshot of the **previous** state |
| `archived_at` | timestamp | when the snapshot was taken |

**`activity_level_threshold`** — the XP curve. `@EmbeddedId` composite key `ActivityLevelThresholdId(activityId, level)` + `xpRequired` (double).

**`processed_event`** — idempotency guard for the RabbitMQ consumer (new in #16):
| Column | Type | Notes |
|--------|------|-------|
| `idempotency_key` | String | **PK** — the activity log's id, as a string; matches `outbox_event.idempotency_key` on the producer side |
| `processed_at` | timestamp | when this event's XP was applied |

The unique PK is what makes redelivery safe: a racing/duplicate delivery hits a constraint violation on `save`, the enclosing `@Transactional` method rolls back (XP not double-applied), and the message gets redelivered — by which point `existsById` is `true` and it's skipped.

## Key internal flow — `LevelTrackerServiceImpl.save()`

Concurrency-safe XP accumulation (fixes the lost-update race — [issue #5](https://github.com/prashant-singh-2001/gamified_tracker/issues/5), merged in [PR #29](https://github.com/prashant-singh-2001/gamified_tracker/pull/29)):

1. **`insertIfAbsent(userId, activityId)`** — native `INSERT … ON CONFLICT (user_id, activity_id) DO NOTHING`. Atomically creates the zero-state row if absent; the affected-row count tells us whether this call **created** the row. `userId` is now an explicit method parameter (from the trusted request header), not read off the request DTO.
2. **`findByUserIdAndActivityIdForUpdate(...)`** — `@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`, locking the row for the rest of the transaction so concurrent updates serialize instead of racing.
3. If the row already existed, **archive the previous state** (snapshot taken *before* any mutation).
4. Add `xp` to `totalXp`, then **`applyLevel(...)`**: query the highest reached threshold and set `level` / `currentLevelXp` via the sealed `LevelOutcome` (`LeveledUp` vs `InProgress`) with `instanceof` pattern matching. Returns a `boolean` — `true` only for a `LeveledUp` outcome on *this* call — used to populate the response's `leveledUp` field.
5. Persist and return the DTO, including the real `leveledUp` value for this call. All read paths (`findAll`, `findById`, `findByUserId`, `findByActivityId`) go through a separate one-arg mapper that always sets `leveledUp: false`.

All in one `@Transactional` method — no retry loop.

## Key internal flow — `ActivityLoggedListener.onActivityLogged()`

The idempotent RabbitMQ consumer (new in #16 — full design: [`EVENT_DRIVEN_DECOUPLING.md`](../docs/features/event-driven-decoupling.md)):

1. `@RabbitListener(queues = "${messaging.queue}")` receives an `ActivityLoggedEvent(logId, userId, activityId, xpEarned)`.
2. **Dedup check:** `processedEventRepository.existsById(logId)` — if already processed, log and return (no-op).
3. **Dedup guard, written first:** `processedEventRepository.save(new ProcessedEvent(logId, now()))`. If a concurrent duplicate already inserted this key, this throws, the `@Transactional` method rolls back, and the message is redelivered — landing on step 2 as a no-op next time.
4. Calls the **same** `LevelTrackerServiceImpl.save(userId, dto)` the HTTP `POST /level` endpoint uses — no business-logic duplication.
5. All in one `@Transactional` method, so the dedup write and the XP application succeed or fail together.

## Configuration

Reads standard env vars (see root [`.env.example`](../.env.example)):

| Var / key | Default | Purpose |
|-----------|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/tracker_db` | DB connection |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `postgres` / `postgres` | DB creds |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | schema management (dev only) |
| `server.port` | `8082` | HTTP port |
| `eureka.client.service-url.defaultZone` | `http://eureka-server:8761/eureka` | registry |
| `SPRING_RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | `rabbitmq` / `5672` / `guest` / `guest` | broker connection |
| `spring.rabbitmq.listener.simple.retry.*` | `enabled: true`, `max-attempts: 3`, `initial-interval: 1000ms` | listener retry before a message is dead-lettered |

## Inter-service dependencies

- **Calls:** nothing over HTTP/Feign (leaf service for outbound calls).
- **Consumes from:** RabbitMQ (`ActivityLoggedEvent`, queue `gamification.activity-logged.q`) — published by activity-service's outbox relay.
- **Called by:** activity-service (indirectly, via RabbitMQ) and directly via `POST /level` (api-gateway or `:8082`).
- **Infra:** Eureka (registration), PostgreSQL (persistence), RabbitMQ (messaging, incl. DLQ).

## Running

**Whole stack (recommended):**
```bash
docker-compose up --build          # from repo root — includes RabbitMQ
```

**Standalone** (needs Postgres + Eureka + RabbitMQ reachable):
```bash
cd gamification-service
mvn spring-boot:run
```

## Testing

```bash
mvn test          # from gamification-service/
```

Covered by `@WebMvcTest` controller tests, `@DataJpaTest` repository tests (incl. `ProcessedEventRepositoryTest`), and Mockito service/component tests — including the `save()` concurrency flow and archive-on-update behavior (`LevelTrackerServiceImplTest`), and the idempotent-consumer dedup logic (`ActivityLoggedListenerTest`, mocked `LevelTrackerService`/`ProcessedEventRepository` — no real broker needed).

> The concurrency fix itself was additionally verified with a live 20-request concurrent burst — exact `totalXp`, a single row, and a coherent archive sequence.

## Troubleshooting

- **Everyone stays at level 1** — no thresholds are seeded by default. `POST /threshold` a curve (e.g. level 2 at `xpRequired: 100`) for the activity first. (A default curve is proposed in [issue #8](https://github.com/prashant-singh-2001/gamified_tracker/issues/8).)
- **`POST /level` returns 400 with a negative-xp message** — `xp` was negative; the DTO rejects it before persistence. **A different 400 (missing header)** means the `userId` request header wasn't sent — required on every direct `POST /level` call (the RabbitMQ consumer path doesn't use this header at all).
- **XP from a logged activity never shows up** — check the RabbitMQ management UI (`http://localhost:15672`, guest/guest) for the `gamification.activity-logged.q` queue depth and its DLQ (`gamification.activity-logged.dlq`); a poison message lands there after 3 failed attempts.
- **Health check:** `curl http://localhost:8082/actuator/health` — `spring-boot-starter-actuator` is wired (exposes `health`, `info`; Docker healthcheck depends on this).
- **Schema/constraint errors on startup after a model change** — `ddl-auto: update` won't retrofit new constraints onto existing data; recreate the dev volume with `docker compose down -v`.

## Related docs

- [Root README](../README.md) · [API.md](../API.md) · [activity-service README](../activity-service/README.md) · [EVENT_DRIVEN_DECOUPLING.md](../docs/features/event-driven-decoupling.md)
