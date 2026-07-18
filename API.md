# API Documentation — Gamified Tracker

This document covers every REST endpoint exposed by the three services: **API Gateway** (public entry point, port `8080`), **Activity Service** (internal, port `8081`), and **Gamification Service** (internal, port `8082`).

In normal use, clients talk **only to the API Gateway**. The Activity Service and Gamification Service endpoints are documented separately below because they're directly reachable in this dev setup (no network isolation yet) and are useful for debugging service-to-service calls.

The Gateway is a real **Spring Cloud Gateway (Server MVC)** — requests are routed declaratively (`lb://activity-service`, `lb://gamification-service` via Eureka), not hand-proxied through controllers. Downstream responses, including error bodies, pass through **unchanged** (see [Error Response Format](#error-response-format)).

## Interactive API Docs (Swagger)

Each service exposes its own OpenAPI UI directly on its own port — these are **not** routed through the Gateway (`/swagger-ui.html` isn't one of the proxied paths):

| Service | Swagger UI | Raw OpenAPI JSON |
|---|---|---|
| API Gateway | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| Activity Service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| Gamification Service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |

The Gateway's own `SecurityConfig` `permitAll`s `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, and `/swagger-resources/**`, so its Swagger UI works without a JWT. Activity Service and Gamification Service have no Spring Security dependency at all, so theirs are open by default too.

---

## Health Checks (Actuator)

All four services — API Gateway, Activity Service, Gamification Service, and Eureka Server — depend on `spring-boot-starter-actuator` and expose the same two endpoints, unauthenticated:

| Service | Health | Info |
|---|---|---|
| API Gateway | http://localhost:8080/actuator/health | http://localhost:8080/actuator/info |
| Activity Service | http://localhost:8081/actuator/health | http://localhost:8081/actuator/info |
| Gamification Service | http://localhost:8082/actuator/health | http://localhost:8082/actuator/info |
| Eureka Server | http://localhost:8761/actuator/health | http://localhost:8761/actuator/info |

Only `health` and `info` are exposed (`management.endpoints.web.exposure.include: health,info`) — no `/actuator/env`, `/actuator/metrics`, etc. `management.endpoint.health.probes.enabled: true` also turns on Kubernetes-style probe groups: `/actuator/health/liveness` and `/actuator/health/readiness`.

On the Gateway specifically, `/actuator/**` is `permitAll` in `SecurityConfig` and exempted in `JwtFilter.shouldNotFilter`, so no JWT is needed. Each service's own Dockerfile bakes a `HEALTHCHECK` against its `/actuator/health`, and `docker-compose.yml` gates every service's startup on its dependencies' health (`depends_on: condition: service_healthy`) in the order postgres → eureka-server → gateway → activity → gamification.

---

## Authentication

All API Gateway endpoints require a JWT **except** `/auth/**`. Obtain a token via register or login, then send it on every subsequent request:

```
Authorization: Bearer <token>
```

Tokens are signed HS256 JWTs and carry the user's `role` as a claim. The signing secret and expiry both come from config (`jwt.secret` / `jwt.expiration`, see `.env` / `JWT_SECRET` / `JWT_EXPIRATION`).

`JwtFilter` grants an authority derived from the token's `role` claim (`ROLE_USER` / `ROLE_ADMIN`). Admin-only routes are enforced **at the URL level** in `SecurityConfig` (`.requestMatchers(HttpMethod.POST, "/api/activity", "/api/activity/").hasRole("ADMIN")`) rather than with a controller-level `@PreAuthorize` — there's no longer a controller to annotate, since routing is now declarative. An `ADMIN` token succeeds on `POST /api/activity`; any other role gets `403`.

**Caller identity on writes:** the JWT also carries a `userId` claim (the numeric `User.id`, set at register/login). `JwtFilter` reads it and injects a `userId` HTTP header on the request before it's routed downstream — overwriting/normalizing any `userId` header the client sent, so it can't be spoofed. `POST /api/activitylog` and `POST /api/level` derive the acting user from this trusted header rather than from the request body (see those endpoints below); the body no longer accepts a `userId` field at all.

---

## API Gateway (port 8080) — public surface

### Auth

#### `POST /auth/register`
Creates a user and returns a JWT. Public (no token required).

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | must be unique |
| `password` | String | hashed with BCrypt before storage |
| `role` | String enum: `USER`, `ADMIN` | optional; defaults to `USER` if omitted. Note: this means any caller can self-register as `ADMIN` — acceptable for this demo app, not production-safe |

**Response:** `200 OK`, body is a raw JWT string (not JSON-wrapped), with the saved role embedded as a claim.

---

#### `POST /auth/login`
Authenticates and returns a JWT. Public (no token required).

**Request body:**
| Field | Type |
|---|---|
| `email` | String |
| `password` | String |

**Response:** `200 OK`, raw JWT string. `401` `ProblemDetail` (`"Invalid email or password"`) if the user doesn't exist or the password doesn't match — the same message either way, so the error doesn't reveal which one failed.

---

### Activity

#### `GET /api/activity`
List all activities. Requires auth (any role).

**Response:** `200 OK`, JSON array of:
| Field | Type |
|---|---|
| `name` | String |
| `category` | enum: `STUDY`, `WORK`, `GAMING`, `CHORES`, `HEALTH`, `OTHER` |
| `xpMultiplier` | double — the **effective** multiplier (per-activity override, else the category base) |
| `active` | boolean |
| `description` | String |
| `createdAt` | ISO-8601 datetime string |

---

#### `GET /api/activity/{name}`
Fetch one activity by name. Requires auth (any role).

- `200 OK` — same shape as above (single object)
- `404` `ProblemDetail` — not found

---

#### `POST /api/activity`
Create an activity. **Requires `ADMIN` role** — a non-admin token gets `403`.

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `name` | String | should be unique (enforced at the DB level) |
| `category` | enum: `STUDY`\|`WORK`\|`GAMING`\|`CHORES`\|`HEALTH`\|`OTHER` | |
| `xpMultiplier` | double | **optional per-activity override.** `≤ 0` or omitted → the activity's `Category` base multiplier applies (`STUDY`/`WORK` 1.5, `HEALTH` 1.3, `OTHER` 1.0, `CHORES` 0.8, `GAMING` 0.5). A positive value overrides that base. e.g. `1.5` |
| `active` | boolean | |
| `description` | String | optional |
| `createdAt` | ISO-8601 datetime string | accepted but **ignored** — the server always sets `createdAt` to the current time |

**Response:** `200 OK`, same shape as `GET /api/activity/{name}`. Note `xpMultiplier` in the response is the **effective** multiplier (the override, or the resolved category base when none was set) — so an activity created without an explicit multiplier reports its category base rather than `0.0`.

---

### Activity Log

#### `GET /api/activitylog/{id}`
Fetch one activity log by its numeric id. Requires auth. **Open read by design** — any authenticated user can look up any log by id, not just their own (players can view each other's activity/stats; this is a social feature, not an oversight).

**Response:** `200 OK` (shape below) or `404` if not found. `bonusApplied`/`bonusMultiplier`/`leveledUp` are always defaulted here, not the real historical values — see the note under `GET /api/activitylog/user/{id}` below.

---

#### `POST /api/activitylog`
Records an activity session and computes XP (with a chance of a bonus roll). Requires auth. **Always writes as the caller** — the acting `userId` is derived server-side from the JWT (via the gateway-injected `userId` header), never from the request body, so one user cannot log activities or grant XP as another user.

**Event-driven, not synchronous** (since issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)): the log is saved and an `ActivityLogged` event is written to an outbox table in the **same transaction**, then relayed to RabbitMQ and consumed asynchronously by the Gamification Service to apply the XP. This endpoint returns as soon as the log + outbox row are persisted — it does **not** wait for XP to actually be applied. See [`EVENT_DRIVEN_DECOUPLING.md`](docs/features/event-driven-decoupling.md).

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `activityName` | String | must match an existing `Activity.name`, else `404` |
| `startTime` | ISO-8601 datetime string | |
| `endTime` | ISO-8601 datetime string | must be after `startTime` |
| `notes` | String | optional |
| `createdAt` | ISO-8601 datetime string | accepted but **ignored** — server sets it to current time |

**Response:** `200 OK`:
| Field | Type | Notes |
|---|---|---|
| `id` | Long | |
| `userId` | Long | |
| `activity` | object | the full `Activity` (id, name, category, xpMultiplier, active, description, createdAt) |
| `startTime` | ISO-8601 datetime string | |
| `endTime` | ISO-8601 datetime string | |
| `durationMinutes` | Long | computed: `endTime - startTime` |
| `xpEarned` | double | computed: `durationMinutes × effectiveMultiplier × bonus`, where `effectiveMultiplier` is the activity's per-activity `xpMultiplier` when set (`> 0`), otherwise its `Category` base multiplier (#10). `bonus` is `1.0` normally, or a random value in `[1.1, 1.5)` on a ~20% chance roll |
| `notes` | String | |
| `createdAt` | ISO-8601 datetime string | |
| `bonusApplied` | boolean | `true` if the ~20% bonus roll succeeded for this session |
| `bonusMultiplier` | double | the multiplier actually used — `1.0` if no bonus, else the rolled `[1.1, 1.5)` value (same value baked into `xpEarned` above) |
| `leveledUp` | boolean | **Always `false` on this response.** XP is now applied asynchronously by the Gamification Service's RabbitMQ consumer, so whether this session leveled the user up isn't known yet at write time. Poll `GET /api/level/user/{id}` shortly after (or watch the level-up notification feed) for the real value. |

- `404` `ProblemDetail` if `activityName` doesn't match any activity.

---

#### `GET /api/activitylog/user/{id}`
List all activity logs for a user. Requires auth. **Open read by design** — `{id}` can be any user, not just the caller (see note above).

**Response:** `200 OK`, JSON array of the same shape as the `POST` response above — **except** `bonusApplied`, `bonusMultiplier` are always `false`/`1.0` here (and on `GET /api/activitylog/{id}`), regardless of what actually happened when the log was created. Those two fields aren't persisted columns; they're only populated on the `POST` response itself, from the in-memory roll. `leveledUp` is `false` everywhere, including on the `POST` response itself now — see the note above.

---

### Level Tracker

**New:** previously only reachable directly on the Gamification Service; now routed through the Gateway too.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/level` | authenticated | list every level-tracker row (all users, all activities) — open read |
| `GET` | `/api/level/{id}` | authenticated | one row by internal id (`404` if missing) — open read, any user's row |
| `POST` | `/api/level` | authenticated | create-or-update XP for **the caller's own** activity, recalculating level. Since issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16), this is no longer called by the Activity Service directly — that happens asynchronously via a RabbitMQ consumer instead (see `POST /api/activitylog` above). This endpoint is still live for direct callers and is a real, synchronous write |
| `GET` | `/api/level/user/{userId}` | authenticated | all rows for a given user — open read, `{userId}` can be anyone. **This is where the real, eventual `leveledUp` outcome of a `POST /api/activitylog` becomes visible**, shortly after the async XP application completes |
| `GET` | `/api/level/activity/{activityId}` | authenticated | all rows for a given activity |

All reads here are **intentionally open** — any authenticated player can view any other player's level/XP stats (see [Authentication](#authentication) and [Gamification Service § Level Tracker](#level-tracker-1)). `POST` is the one write: the `userId` field has been removed from the request body — the acting user comes from the trusted `userId` header instead, so XP can only ever be granted to the caller. Request/response bodies otherwise mirror the Gamification Service (`activityId`, `level`, `totalXp`, `currentLevelXp`, `leveledUp`) — **`leveledUp` is only ever `true` on the `POST` response that actually crossed a threshold; every `GET` endpoint here hardcodes it to `false`**, regardless of the row's real state.

---

### Activity Level Threshold

**New:** previously only reachable directly on the Gamification Service; now routed through the Gateway too. Defines the XP required to reach each level, per activity.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/threshold` | authenticated | list all thresholds |
| `POST` | `/api/threshold/activity` | authenticated | look up one threshold by composite key (a read, despite `POST`) |
| `POST` | `/api/threshold` | authenticated | create (or overwrite) a threshold |

Request/response bodies mirror the Gamification Service (`activityId`, `level`, `xpRequired`) — see [Gamification Service § Activity Level Threshold](#activity-level-threshold-1) below.

---

### Misc

#### `GET /api/hello`
Returns `"Hello {email}"` for the authenticated caller. Requires auth. Diagnostic/example endpoint only.

---

## Activity Service (port 8081) — internal

Base path `/activity` and `/activitylog`. No auth layer of its own — auth is only enforced at the Gateway.

#### `GET /activity/`
List all activities. Same response shape as the Gateway's `GET /api/activity`.

#### `GET /activity/{name}`
Fetch one activity by name. `200 OK` with the activity, or `404` `ProblemDetail` (`"Activity not found: {name}"`) if missing.

#### `POST /activity/`
Create an activity. Same request/response shape as the Gateway's `POST /api/activity` (no role check at this layer — that's Gateway-only).

#### `GET /activitylog/{id}`
Fetch one activity log by id. `200 OK` or `404` `ProblemDetail` (`"Activity log not found: {id}"`).

#### `POST /activitylog/`
Create an activity log (computes duration + XP bonus, saves the log, and writes an outbox row for async XP application — see [`EVENT_DRIVEN_DECOUPLING.md`](docs/features/event-driven-decoupling.md); no synchronous call to Gamification Service). Same request/response shape as the Gateway's `POST /api/activitylog`, including the now-always-`false` `leveledUp`. `404` `ProblemDetail` if `activityName` doesn't match an existing activity. Reads `userId` from the `userId` request header (required) rather than the body — when called through the Gateway this header is the trusted, JWT-derived value; called directly against `:8081` (bypassing the Gateway, as this dev setup allows), the header is unauthenticated and effectively caller-supplied, since this service has no security layer of its own.

#### `GET /activitylog/user/{id}`
List all activity logs for a user.

---

## Gamification Service (port 8082) — internal

Base paths `/level` and `/threshold`. No auth layer of its own.

### Level Tracker

#### `GET /level`
List every `LevelTracker` row (all users, all activities).

**Response shape** (all endpoints below return this):
| Field | Type | Notes |
|---|---|---|
| `userId` | Long | |
| `activityId` | Long | |
| `level` | Integer | |
| `totalXp` | double | total accumulated XP for this user+activity |
| `currentLevelXp` | double | XP accumulated within the current level |
| `leveledUp` | boolean | `true` only on the `POST /level` response that actually crossed a threshold on that call. **Every `GET` endpoint below hardcodes this to `false`**, even for a row currently above level 1 — it's not derived from stored state, only from the outcome of the specific write that populated it. |

#### `GET /level/{id}`
Fetch one `LevelTracker` by its internal numeric id. `200 OK` or `404` `ProblemDetail` (`"LevelTracker with id: {id} not found"`).

#### `POST /level`
Create-or-update a user's XP for an activity, recalculating level. Reads `userId` from the `userId` request header (required), not the body — trustworthy through the Gateway, caller-supplied if hit directly on `:8082`.

**Two callers now** (since issue [#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)): this HTTP endpoint (still reachable directly, unchanged), and `ActivityLoggedListener`, a `@RabbitListener` that calls `LevelTrackerServiceImpl.save(userId, dto)` **in-process** for each async `ActivityLogged` event — the consumer path doesn't go through this controller or the `userId` header at all; `userId` comes from the event payload instead, and duplicate deliveries are deduped against a `processed_event` table before `save` is ever invoked.

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `activityId` | Long | |
| `xp` | double | XP to add. **Must be ≥ 0** — negative values are rejected with a `400` `ProblemDetail` (`"Invalid request body"`) before reaching the database |

**Response:** `200 OK`, the resulting `LevelTrackerDto` (shape above, including the real `leveledUp` value for this call). Level-up logic: crosses the highest `ActivityLevelThreshold` whose `xpRequired` is ≤ the new total XP for that activity; `currentLevelXp` becomes `totalXp − threshold.xpRequired`.

#### `GET /level/user/{userId}`
List all `LevelTracker` rows for a given user (one per activity they've logged). Open read — no ownership check; any caller can pass any `{userId}`.

#### `GET /level/activity/{activityId}`
List all `LevelTracker` rows for a given activity (one per user who's logged it).

---

### Activity Level Threshold

Defines the XP required to reach each level, per activity.

#### `GET /threshold`
List all thresholds.

**Response shape:**
| Field | Type |
|---|---|
| `activityId` | Long |
| `level` | Integer |
| `xpRequired` | double |

#### `POST /threshold/activity`
Look up a single threshold by composite key (despite the `POST`, this is a read — the body is used purely as a key, not persisted).

**Request body:** `{ "activityId": Long, "level": Integer }` (`xpRequired` is ignored).

**Response:** `200 OK` with the matching threshold, or `404` `ProblemDetail` (`"ActivityLevelThreshold not found"`) if no match.

#### `POST /threshold`
Create (or overwrite) a threshold.

**Request body:** full shape above (`activityId`, `level`, `xpRequired`).

**Response:** `200 OK`, the saved threshold.

---

## Error Response Format

Most `404` responses across all three services use Spring's RFC 7807 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Activity not found: Study",
  "instance": "/activity/Study"
}
```

`401` (`POST /auth/login` failures) and `400` (validation failures on `POST /level`) also use `ProblemDetail`. Any route that fails to match at all (e.g. a typo'd path) still falls back to Spring's default whitelabel error body, since that never reaches application code.

**Through the Gateway, downstream error bodies pass through byte-for-byte unchanged** — including the `instance` field, which still shows the *downstream* service's own path (e.g. `/level/999999`), not the Gateway's `/api/level/999999`. This is because routing is a real reverse proxy (Spring Cloud Gateway), not a hand-rolled wrapper that re-serializes responses. Verified: `GET /api/activity/does-not-exist` and `GET /api/level/999999` both return the exact same `ProblemDetail` body their respective service returns directly.

**`429 Too Many Requests`** is returned by the Gateway's rate limiter when a caller exceeds a route's token bucket (Redis-backed Bucket4j — see [api-gateway README § Rate limiting](api-gateway/README.md#rate-limiting)). Rate-limited responses carry an **`X-RateLimit-Remaining`** header (tokens left in the current window). The proxied-route `429` is emitted by the Gateway filter; the `/auth/**` brute-force guard returns a small JSON body `{"error":"Too many requests"}`. Limits are keyed per authenticated `userId` (falling back to client IP), so one user hitting their limit never throttles another.

---

## Known Issues Summary

All previously-tracked issues in this section have been resolved and verified end-to-end:

- ~~`ActivityController` route bug (stray whitespace) breaking `GET /activity/{name}`~~ — fixed.
- ~~`@PreAuthorize("hasRole('ADMIN')")` inert due to missing `@EnableMethodSecurity` + `JwtFilter` granting no authorities~~ — fixed; non-admin tokens now get a real `403`.
- ~~`AuthService.register` ignoring the requested `role`~~ — fixed; note the resulting tradeoff documented above (self-service `ADMIN` registration).
- ~~`JwtUtil` ignoring the `jwt.expiration` config~~ — fixed; confirmed the token's `exp` claim moves when the config value changes.
- ~~`LevelTrackerService.mapToDto` always returning `totalXp: 0.0`~~ — fixed.
- ~~Inconsistent error shapes (raw `500`s / generic bodies instead of `ProblemDetail`)~~ — fixed for login failures and negative-`xp` validation.
- ~~IDOR on writes: `POST /api/activitylog` and `POST /api/level` trusted a client-supplied `userId` in the body, so any authenticated user could log activities or grant XP **as any other user**~~ — fixed. The JWT now carries the numeric `userId`; `JwtFilter` injects it as a trusted `userId` header (overwriting/stripping any client-sent value) before the request is routed downstream; the write DTOs no longer accept `userId` in the body at all. (Historical note: this originally also covered activity-service's internal Feign call to gamification-service — that call no longer exists, see the next item.)
- ~~`POST /api/activitylog` called Gamification Service *before* saving the log, so a gamification outage lost the activity log entirely~~ ([#4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4)) — fixed via event-driven decoupling ([#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)): the log is saved first, an outbox row is written in the same transaction, and XP is applied asynchronously by a RabbitMQ consumer. Trade-off: `leveledUp` is no longer available synchronously — see the Level Tracker/Activity Log sections above. See [`EVENT_DRIVEN_DECOUPLING.md`](docs/features/event-driven-decoupling.md).

Remaining non-issues, documented for awareness rather than as defects: `createdAt` is always server-set (client-supplied values on create endpoints are accepted but ignored); any caller can self-register as `ADMIN` (acceptable for a demo app); **reads are intentionally open** — any authenticated user can view any other user's activity logs and level/XP stats (`GET .../{id}`, `GET .../user/{id}`) as a deliberate social/leaderboard-style feature, not an access-control gap; **`bonusApplied`/`bonusMultiplier` are only ever real on the specific `POST` response that computed them** — every `GET` endpoint that returns an `ActivityLogResponse` hardcodes them to `false`/`1.0`, since they're not persisted, only computed in-memory at creation time; and **`leveledUp` is now `false` everywhere except the `POST /api/level`/`POST /level` response that actually crossed a threshold** (including on `POST /api/activitylog`/`POST /activitylog/`, since that write no longer applies XP synchronously — see Event-Driven Decoupling above). Direct calls to `:8081`/`:8082` bypassing the Gateway are also unauthenticated, since neither service has its own security layer — the `userId` header is only trustworthy when it arrives via the Gateway.
