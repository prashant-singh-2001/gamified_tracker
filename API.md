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

On the Gateway specifically, `/actuator/**` is `permitAll` in `SecurityConfig` and exempted in `UserIdHeaderFilter.shouldNotFilter`, so no JWT is needed. Each service's own Dockerfile bakes a `HEALTHCHECK` against its `/actuator/health`, and `docker-compose.yml` gates every service's startup on its dependencies' health (`depends_on: condition: service_healthy`) in the order postgres → eureka-server → gateway → activity → gamification.

---

## Authentication

All API Gateway endpoints require a JWT **except** `/auth/**`. Obtain a token via register or login, then send it on every subsequent request:

```
Authorization: Bearer <token>
```

Tokens are signed HS256 JWTs and carry the user's `role` as a claim. The signing secret and expiry both come from config (`jwt.secret` / `jwt.expiration`, see `.env` / `JWT_SECRET` / `JWT_EXPIRATION`).

`SecurityConfig` configures the OAuth2 Resource Server, validates JWTs via the configured `JwtDecoder`, and uses a `JwtAuthenticationConverter` to derive Spring Security authorities from the token's `role` claim (`ROLE_USER` / `ROLE_ADMIN`). Admin-only routes are enforced **at the URL level**, e.g. `.requestMatchers(HttpMethod.POST, "/api/activity", "/api/activity/").hasRole("ADMIN")` — three such matchers exist today (`POST /api/activity`, `/api/activitylog/review/**`, `POST /api/level`). An `ADMIN` token succeeds on all three; any other role receives `403 Forbidden`. **The only way to obtain an `ADMIN` token is via an out-of-band-provisioned account** — `POST /auth/register` always assigns `Role.USER` regardless of what the client sends (see `AdminBootstrap` under Auth below).

**Caller identity on writes:** the JWT also carries a `userId` claim (the numeric `User.id`, set at register/login). `UserIdHeaderFilter` reads it and injects a trusted `userId` HTTP header on the request before it is routed downstream — overwriting/normalizing any `userId` header the client sent, so it can't be spoofed. `POST /api/activitylog` derives the acting user entirely from this trusted header; `POST /api/level` uses it to identify the **acting admin** (for the audit trail) while the XP **target** user is a separate, explicit field in the body (see that endpoint below) — neither body ever accepts a raw `userId` field.

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

**No `role` field** — every account created here is `Role.USER`, unconditionally (issue #74; a client-supplied `role` used to be honored, letting any unauthenticated caller self-promote to `ADMIN` on this `permitAll` endpoint). An `ADMIN` account can only be provisioned out-of-band via `AdminBootstrap`, an `ApplicationRunner` gated by `app.admin.bootstrap.enabled` (off by default — see `.env.example`), which creates or promotes the configured email at startup.

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

**Session integrity** (issue #67): duration is bounded and screened before any XP is committed. A session over `session-integrity.max-duration-minutes` (default 1440, i.e. 24h) or one that would push the caller's running total for that calendar day over `session-integrity.max-daily-minutes` (default 1440) is rejected outright with `400`. A session that passes both caps but is a statistical outlier against the caller's own (or, for new users, the category-wide) duration history — or simply exceeds `session-integrity.absolute-flag-minutes` (default 600) regardless of history — is still accepted and saved, but quarantined: see `reviewStatus` in the response table below and [Session Integrity](docs/features/session-integrity.md) for the full mechanism.

**Fuzzy `activityName` resolution** (issue #66): a miss on the exact name is no longer a dead end. The catalog's name, description, and category are scored against what was typed; a confident, unambiguous match against an **active** activity is substituted automatically (see `nameResolution` below), everything else returns ranked `suggestions` on the `404`. See [Fuzzy Activity-Name Matching](docs/features/fuzzy-activity-matching.md) for the algorithm and the safety rails guarding the auto-resolve.

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `activityName` | String | matched exactly first; on a miss, fuzzy-resolved against every activity's name/description/category (#66) — see below |
| `startTime` | ISO-8601 datetime string | must be `@PastOrPresent` |
| `endTime` | ISO-8601 datetime string | must be after `startTime` **and** `@PastOrPresent` (#67) — a future-dated session is `400`, not accepted. This closed a real exploit: `endTime` previously had no upper bound at all, so `endTime = now + 10 years` was accepted and awarded millions of minutes of XP from a single call |
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
| `xpEarned` | double | computed: `durationMinutes × effectiveMultiplier × bonus`, where `effectiveMultiplier` is the activity's per-activity `xpMultiplier` when set (`> 0`), otherwise its `Category` base multiplier (#10). `bonus` is `1.0` normally, or a random value in `[1.1, 1.5)` on a ~20% chance roll. Frozen on the row at write time regardless of `reviewStatus` below — a later approval awards exactly this number |
| `notes` | String | |
| `createdAt` | ISO-8601 datetime string | |
| `bonusApplied` | boolean | `true` if the ~20% bonus roll succeeded for this session |
| `bonusMultiplier` | double | the multiplier actually used — `1.0` if no bonus, else the rolled `[1.1, 1.5)` value (same value baked into `xpEarned` above) |
| `leveledUp` | boolean | **Always `false` on this response.** XP is now applied asynchronously by the Gamification Service's RabbitMQ consumer, so whether this session leveled the user up isn't known yet at write time. Poll `GET /api/level/user/{id}` shortly after (or watch the level-up notification feed) for the real value. |
| `reviewStatus` | String enum | (#67) `CLEARED` (default — XP applies normally), `FLAGGED` (statistical outlier or over `absolute-flag-minutes`; the outbox row was **not** written, so this session's XP will not reach the leaderboard until a maintainer approves it — see [Session Integrity Review](#session-integrity-review-admin) below), `APPROVED`/`REJECTED` (a maintainer's decision on a previously-`FLAGGED` log; never the value on a fresh `POST` response) |
| `nameResolution` | object \| null | (#66) present **only** when `activityName` didn't match exactly and was fuzzy-resolved to a different one: `{ requestedName, resolvedName, score }`. `null` on an exact match — the substitution is never silent |

- `404` `ProblemDetail` if `activityName` doesn't match any activity closely enough to auto-resolve (issue #66). The body carries ranked alternatives, not just the bare message:
  ```json
  { "status": 404, "detail": "Activity not found: morning jog", "requestedName": "morning jog",
    "suggestions": [{ "name": "Running", "category": "HEALTH", "active": true, "score": 0.589, "matchedOn": "DESCRIPTION" }] }
  ```
  `suggestions` is always present (`[]` when nothing cleared the floor). See [Fuzzy Activity-Name Matching](docs/features/fuzzy-activity-matching.md).
- `400` `ProblemDetail` if `endTime` is in the future, or the session exceeds either duration cap (see Session Integrity above) — distinct from a `FLAGGED` `200`, which is accepted input that's merely statistically unusual.

---

#### `GET /api/activitylog/user/{id}`
List all activity logs for a user. Requires auth. **Open read by design** — `{id}` can be any user, not just the caller (see note above).

**Response:** `200 OK`, JSON array of the same shape as the `POST` response above — **except** `bonusApplied`, `bonusMultiplier` are always `false`/`1.0` here (and on `GET /api/activitylog/{id}`), regardless of what actually happened when the log was created. Those two fields aren't persisted columns; they're only populated on the `POST` response itself, from the in-memory roll. `leveledUp` is `false` everywhere, including on the `POST` response itself now — see the note above.

---

### Session Integrity Review (Admin)

Issue #67. The queue of `FLAGGED` activity logs (see `reviewStatus` above) and the two decisions a maintainer can make on each one. **Admin-only** — unlike every other endpoint in this section, these are gated `hasRole("ADMIN")` at the Gateway (`SecurityConfig`, same pattern as `POST /api/activity`), not open reads. Mounted under `/api/activitylog/review/**` so it rides the existing `activitylog` route/rate-limit bucket rather than needing a new one.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/activitylog/review/flagged` | **ADMIN** | the review queue, newest first. Each entry pairs the full `ActivityLogResponse` (as in `POST /api/activitylog` above) with the detector's recomputed verdict: `modifiedZScore`, `median`, `sampleSize`, `basis` (`INSUFFICIENT_SAMPLES` \| `MODIFIED_Z_SCORE` \| `MEAN_AD_FALLBACK` \| `RELATIVE_FALLBACK` \| `ABSOLUTE_THRESHOLD`) |
| `POST` | `/api/activitylog/review/{id}/approve` | **ADMIN** | `FLAGGED` → `APPROVED`, and writes the outbox row that was withheld at creation time — the existing 2-second `OutboxRelay` then applies the originally-computed `xpEarned` exactly as if the log had never been flagged. Idempotent: the outbox row's `idempotency_key` is the log id, so a second approve attempt on an already-`APPROVED` log can't double-award XP |
| `POST` | `/api/activitylog/review/{id}/reject` | **ADMIN** | `FLAGGED` → `REJECTED`. No outbox row is ever written — XP for this log is never applied. No compensation logic needed, since nothing was written at creation time |

Both `POST` endpoints return the updated `ActivityLogResponse` (`200 OK`), or `409` `ProblemDetail` if the log isn't currently `FLAGGED` (e.g. re-approving an already-`APPROVED`/`REJECTED` log, or acting on a log that was always `CLEARED`), or `404` if the id doesn't exist. See [Session Integrity](docs/features/session-integrity.md) for the detection math and the full flagged → approved/rejected lifecycle.

---

### Level Tracker

**New:** previously only reachable directly on the Gamification Service; now routed through the Gateway too.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/level` | authenticated | list every level-tracker row (all users, all activities) — open read |
| `GET` | `/api/level/{id}` | authenticated | one row by internal id (`404` if missing) — open read, any user's row |
| `POST` | `/api/level` | **ADMIN** | manually award XP to an activity, recalculating level. See below — not a general-purpose write, and not what the normal activity-logging flow uses |
| `GET` | `/api/level/user/{userId}` | authenticated | all rows for a given user — open read, `{userId}` can be anyone. **This is where the real, eventual `leveledUp` outcome of a `POST /api/activitylog` becomes visible**, shortly after the async XP application completes |
| `GET` | `/api/level/activity/{activityId}` | authenticated | all rows for a given activity |

All reads here are **intentionally open** — any authenticated player can view any other player's level/XP stats (see [Authentication](#authentication) and [Gamification Service § Level Tracker](#level-tracker-1)).

**`POST /api/level` used to be a public, unbounded XP mint — fixed in issue #74.** Before the fix, any authenticated user could call it with an arbitrary `activityId`/`xp` and grant themselves unlimited XP, bypassing activity-service, the outbox, and the idempotency guard entirely. It's now:
- **Gated `hasRole("ADMIN")`** at the Gateway — a non-admin token gets `403`.
- **Capped at 10,000 XP per call** (`@DecimalMax` on the request's `xp` field) — comfortably above the richest realistic single logged session (~4,860 XP), but well short of an arbitrary mint. A request over the cap is `400` with a message naming the limit.
- **Audited.** Every call writes a `manual_xp_award` row (acting admin, target user, activity, xp, timestamp) before the XP is applied — see [Concurrency-Safe XP Accumulation](docs/features/concurrency-safe-xp.md).

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `targetUserId` | Long | optional — the user to award XP to. Omit to award to the calling admin's own account |
| `activityId` | Long | required |
| `xp` | double | `@PositiveOrZero`, capped at `10000.0` |

Request/response bodies otherwise mirror the Gamification Service (`activityId`, `level`, `totalXp`, `currentLevelXp`, `leveledUp`) — **`leveledUp` is only ever `true` on the `POST` response that actually crossed a threshold; every `GET` endpoint here hardcodes it to `false`**, regardless of the row's real state. The normal way a player earns XP remains `POST /api/activitylog`, applied asynchronously — this endpoint is a separate, admin-only manual-award tool, not an alternate path for players to grant themselves XP.

---

### Activity Level Threshold

**New:** previously only reachable directly on the Gamification Service; now routed through the Gateway too. Defines the XP required to reach each level, per activity.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/threshold` | authenticated | list all thresholds |
| `GET` | `/api/threshold/activity/{activityId}?upToLevel=10` | authenticated | the activity's **effective** ladder: its explicit rows if it has any, otherwise the default curve generated for levels 1..`upToLevel`. Nothing is persisted either way |
| `POST` | `/api/threshold/activity` | authenticated | look up one threshold by composite key (a read, despite `POST`) |
| `POST` | `/api/threshold` | authenticated | create (or overwrite) a threshold |

Request/response bodies mirror the Gamification Service (`activityId`, `level`, `xpRequired`) — see [Gamification Service § Activity Level Threshold](#activity-level-threshold-1) below.

**Not yet exposed here:** achievement badges are fully implemented in `gamification-service`
(`AchievementServiceImpl.evaluateAndAward`) but have no HTTP endpoint and no production trigger yet —
see [Achievement Badges](docs/features/achievement-badges.md) for the honest-gap writeup.

---

### Analytics

Routed through the Gateway via the same `/api/activitylog/**` match as Activity Log above (there is
no separate `/api/analytics/**` route). See [Analytics](docs/features/analytics.md) for the full
design writeup, including the in-memory-aggregation trade-off and known gaps.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/activitylog/analytics/user/{userId}/category-summary` | authenticated | totals per `Category`: `totalDurationMinutes`, `totalXpEarned`, `totalSessions`. Open read — `{userId}` can be anyone |
| `GET` | `/api/activitylog/analytics/user/{userId}/xp-over-time?days=7` | authenticated | one `{date, totalXpEarned, totalDurationMinutes}` entry per day in the window, **zero-filled** — always returns exactly `days` entries regardless of how many have logs |
| `GET` | `/api/activitylog/analytics/user/{userId}/weekly-report` | authenticated | `currentWeekXp`, `previousWeekXp`, `percentageChange` (`100.0` if the previous week was `0` and this week isn't, `0.0` if both are `0`), `totalActiveMinutes`, `topCategory` (`null` if the week has no logs), `dailyBreakdown` (7 zero-filled entries for the current week) |

---

### Notifications

Surfaces level-up events (`LevelUpEvent`) as a caller-scoped feed. Full detail in
[Level-Up Notifications](docs/features/level-up-notifications.md).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/notifications?unreadOnly=false` | authenticated | the caller's own level-up events, newest first. `unreadOnly=true` filters to unread only |
| `GET` | `/api/notifications/unread-count` | authenticated | `{"Count": <long>}` |
| `POST` | `/api/notifications/{id}/read` | authenticated | marks one notification read. `204 No Content`, or `404` if `{id}` doesn't belong to the caller (ownership is enforced — this is not an open read like Level Tracker/Activity Log) |

**Response shape** (list endpoint): `id`, `activityId`, `oldLevel`, `newLevel`, `totalXp`,
`currentLevelXp`, `read` (boolean), `createdAt`.

---

### Leaderboard

Live-computed ranking (`SUM(level_tracker.total_xp) GROUP BY user_id`), not a materialized
snapshot — see [Rank & Level System](docs/features/rank-and-level-system.md) for how this differs
from `/api/ranks` below.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/leaderboard?page=0&size=20` | authenticated | global leaderboard, paged. **`page` and `size` are both required** — omitting either is a `400` (no defaults at this endpoint) |
| `GET` | `/api/leaderboard/activity/{activityId}?page=0&size=20` | authenticated | leaderboard scoped to one activity, same paging rule |
| `GET` | `/api/leaderboard/me` | authenticated | the caller's own global rank as a bare integer (not wrapped in an object) |

**Response shape** (paged endpoints): array of `{rank, userId, totalXp}`.

---

### Ranks

A separate, materialized ranking system from Leaderboard above — percentile-based tiers
(`SUMMIT`…`BASECAMP`) recomputed on a schedule into `user_rank`, so reads here are O(1) rather than
a live aggregate query. See [Rank & Level System](docs/features/rank-and-level-system.md).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/ranks/me` | authenticated | the caller's `RankCardDto`: `tier`, `overallLevel`, `totalXp`, `percentile`, `position`, `totalUsers`, `updatedAt`. `404` if the caller has no rank yet (never recomputed) |
| `GET` | `/api/ranks/{tier}/leaderboard?page=0&size=20` | authenticated | members of one tier (`SUMMIT`, `PEAK`, `RIDGE`, `ALPINE`, `ASCENT`, `HIGHLAND`, `FOOTHILL`, `TRAILHEAD`, `BASECAMP`), each `{withinRankPosition, userId, totalXp, overallLevel, tier}` |
| `GET` | `/api/ranks/me/leaderboard?page=0&size=20` | authenticated | same shape, scoped to the caller's own tier. `404` if the caller has no rank yet |
| `GET` | `/api/ranks` | authenticated | tier distribution: array of `{tier, userCount}` |
| `POST` | `/api/ranks/recompute` | authenticated | forces an off-schedule recompute (normally runs every `ranking.recompute-interval-ms`, default 5 min). Returns `{"rankedUsers": <int>}`. **No admin guard** — any authenticated caller can trigger this |

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

### Analytics

#### `GET /activitylog/analytics/user/{userId}/category-summary`
Aggregates activity logs for a user grouped by category (`STUDY`, `WORK`, `GAMING`, `CHORES`, `HEALTH`, `OTHER`). Returns JSON array containing `category`, `totalDurationMinutes`, `totalXpEarned`, and `totalSessions`.

#### `GET /activitylog/analytics/user/{userId}/xp-over-time?days=7`
Calculates daily XP earned and total active minutes for the specified window (default 7 days). Returns JSON array of daily breakdown objects.

#### `GET /activitylog/analytics/user/{userId}/weekly-report`
Generates a comprehensive weekly report comparing current week vs previous week XP, percentage change, total active minutes, top category, and a 7-day daily breakdown.

#### `GET /activitylog/review/flagged`
#### `POST /activitylog/review/{id}/approve`
#### `POST /activitylog/review/{id}/reject`
Direct-hit equivalents of the Gateway's [Session Integrity Review](#session-integrity-review-admin) endpoints (issue #67). Same request/response shapes. **The `hasRole("ADMIN")` gate is Gateway-only** — this service has no security layer of its own, so calling these directly against `:8081` bypasses the admin check entirely, same caveat as the trusted-`userId`-header note on `POST /activitylog/` above.

---

## Gamification Service (port 8082) — internal

Base paths `/level`, `/threshold`, `/notifications`, `/leaderboard`, and `/ranks`. No auth layer of
its own — the internal sections below cover `/level` and `/threshold` in full; `/notifications`,
`/leaderboard`, and `/ranks` are documented above under the Gateway's public surface (same request/
response shapes, just called directly on `:8082` instead of proxied).

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
| `xpForNextLevel` | double | XP still needed to reach the next level, rounded to 2dp. `0.0` when there is no next level to reach |
| `progressPercent` | double | how far through the **current level's band** the user is, 0–100, rounded to 2dp. Reaching a new level resets this to ~0, it does not keep climbing toward 100 across levels. `100.0` when topped out |
| `leveledUp` | boolean | `true` only on the `POST /level` response that actually crossed a threshold on that call. **Every `GET` endpoint below hardcodes this to `false`**, even for a row currently above level 1 — it's not derived from stored state, only from the outcome of the specific write that populated it. |

**Honest gap:** unlike the `level`/`currentLevelXp` fields (which do fall back to the formula-driven default curve for activities with no explicit thresholds — see [Leveling Engine](docs/features/leveling-engine.md)), `xpForNextLevel`/`progressPercent` are resolved from explicit `activity_level_threshold` rows only. An activity running on the default curve has no next-threshold row, so these two report `xpForNextLevel: 0.0, progressPercent: 100.0` even while its level keeps climbing — the progress bar and the level disagree for unseeded activities.

#### `GET /level/{id}`
Fetch one `LevelTracker` by its internal numeric id. `200 OK` or `404` `ProblemDetail` (`"LevelTracker with id: {id} not found"`).

#### `POST /level`
**Admin-only manual XP award (issue #74)** — `LevelTrackerController.awardXpManually`. Reads the acting admin's id from the `userId` request header (required) — trustworthy through the Gateway, caller-supplied if hit directly on `:8082`, since this service has no security layer of its own (the `hasRole("ADMIN")` gate is Gateway-only; see the caveat on the review endpoints below for the same limitation). Was previously `createLevelTracker`, a public, unbounded write reachable by any authenticated user — see [Concurrency-Safe XP Accumulation](docs/features/concurrency-safe-xp.md) for the fix.

**Two callers now:** this HTTP endpoint, and `ActivityLoggedListener`, a `@RabbitListener` that calls `LevelTrackerServiceImpl.save(userId, dto)` **in-process** for each async `ActivityLogged` event — the consumer path doesn't go through this controller, the `userId` header, or the admin gate at all; `userId` comes from the event payload instead, and duplicate deliveries are deduped against a `processed_event` table before `save` is ever invoked. This HTTP endpoint no longer calls `save` directly either — it goes through `awardManually`, which writes a `manual_xp_award` audit row first, then delegates to the same `save`.

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `targetUserId` | Long | optional — defaults to the acting admin (the `userId` header) when omitted |
| `activityId` | Long | required |
| `xp` | double | `@PositiveOrZero` **and** `@DecimalMax(10000.0)` — either violation is `400` with the specific message (e.g. `"xp exceeds the per-award cap of 10000"`), via gamification-service's `MethodArgumentNotValidException` handler added alongside this fix (previously this service had no such handler at all — see [Error Handling § Known edges](docs/features/error-handling.md)) |

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

## Config Service (port 8888)

A Spring Cloud Config Server (`native` profile, filesystem-backed) — it runs, and answers config
requests correctly, but **no other service imports it yet**. See
[Config Server](docs/features/config-server.md) for the full Phase 1 writeup.

```bash
curl http://localhost:8888/activity-service/default
```

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

`401` (`POST /auth/login` failures) and `400` (validation failures on activity-service endpoints) also use `ProblemDetail`. Validation `400`s carry every field violation joined into `detail`, e.g. `"Activity name is required; Start time is required"`.

**Not this shape:** the `401` for a missing/expired/malformed bearer token and the `403` for a caller without the required role are written by Spring Security's filter chain, before any controller advice runs. Neither is customized in this project, so both use Spring's defaults — an **empty body** plus a `WWW-Authenticate: Bearer` header explaining the failure, not a `ProblemDetail`. This is the one place a client parses a different error shape than everywhere else in the API.

Any route that fails to match at all (e.g. a typo'd path) still falls back to Spring's default whitelabel error body, since that never reaches application code.

**Through the Gateway, downstream error bodies pass through byte-for-byte unchanged** — including the `instance` field, which still shows the *downstream* service's own path (e.g. `/level/999999`), not the Gateway's `/api/level/999999`. This is because routing is a real reverse proxy (Spring Cloud Gateway), not a hand-rolled wrapper that re-serializes responses. Verified: `GET /api/activity/does-not-exist` and `GET /api/level/999999` both return the exact same `ProblemDetail` body their respective service returns directly. **This guarantee used to be broken for every downstream error** (issue #95 — see Known Issues below); it's a `dispatcherTypeMatchers(...).permitAll()` in the Gateway's `SecurityConfig`, not an accident of the reverse-proxy setup alone, that keeps it true.

**RFC 7807 extension members carry structured detail beyond `detail`.** `POST /api/activitylog`'s `404` on an unresolved `activityName` (issue #66) is the one place today: `requestedName` (String) and `suggestions` (array, `[]` when empty) ride alongside the standard fields — see [Fuzzy Activity-Name Matching](docs/features/fuzzy-activity-matching.md). Extension properties are the RFC-sanctioned way to extend `ProblemDetail`; this doesn't introduce a second error shape.

**`429 Too Many Requests`** is returned by the Gateway's rate limiter when a caller exceeds a route's token bucket (Redis-backed Bucket4j — see [api-gateway README § Rate limiting](api-gateway/README.md#rate-limiting)). Rate-limited responses carry an **`X-RateLimit-Remaining`** header (tokens left in the current window). The proxied-route `429` is emitted by the Gateway filter; the `/auth/**` brute-force guard returns a small JSON body `{"error":"Too many requests"}`. Limits are keyed per authenticated `userId` (falling back to client IP), so one user hitting their limit never throttles another.

---

## Known Issues Summary

All previously-tracked issues in this section have been resolved and verified end-to-end:

- ~~`ActivityController` route bug (stray whitespace) breaking `GET /activity/{name}`~~ — fixed.
- ~~`@PreAuthorize("hasRole('ADMIN')")` inert due to missing `@EnableMethodSecurity` + the old `JwtFilter` granting no authorities~~ — fixed; non-admin tokens now get a real `403`. Authorities are now mapped from the `role` claim by `SecurityConfig`'s `JwtAuthenticationConverter`, and gating is by URL rather than `@PreAuthorize`.
- ~~`AuthService.register` ignoring the requested `role`~~ — this "fix" was itself a vulnerability (issue #74): honoring a client-supplied `role` let any unauthenticated caller self-register as `ADMIN` on the `permitAll` auth endpoint. `register` now always assigns `Role.USER`, full stop; `RegisterRequest` no longer has a `role` field to send. See `POST /auth/register` above and `AdminBootstrap` for out-of-band provisioning.
- ~~`JwtUtil` ignoring the `jwt.expiration` config~~ — fixed; confirmed the token's `exp` claim moves when the config value changes.
- ~~`LevelTrackerService.mapToDto` always returning `totalXp: 0.0`~~ — fixed.
- ~~Inconsistent error shapes (raw `500`s / generic bodies instead of `ProblemDetail`)~~ — fixed for login failures and negative-`xp` validation.
- ~~IDOR on writes: `POST /api/activitylog` and `POST /api/level` trusted a client-supplied `userId` in the body, so any authenticated user could log activities or grant XP **as any other user**~~ — fixed. The JWT now carries the numeric `userId`; `UserIdHeaderFilter` injects it as a trusted `userId` header (overwriting/stripping any client-sent value) before the request is routed downstream; the write DTOs no longer accept `userId` in the body at all. (Historical notes: this originally also covered activity-service's internal Feign call to gamification-service — that call no longer exists, see the next item. The filter was previously named `JwtFilter` and also did the token parsing; that half now belongs to Spring Security's OAuth2 resource server.)
- ~~`POST /api/activitylog` called Gamification Service *before* saving the log, so a gamification outage lost the activity log entirely~~ ([#4](https://github.com/prashant-singh-2001/gamified_tracker/issues/4)) — fixed via event-driven decoupling ([#16](https://github.com/prashant-singh-2001/gamified_tracker/issues/16)): the log is saved first, an outbox row is written in the same transaction, and XP is applied asynchronously by a RabbitMQ consumer. Trade-off: `leveledUp` is no longer available synchronously — see the Level Tracker/Activity Log sections above. See [`EVENT_DRIVEN_DECOUPLING.md`](docs/features/event-driven-decoupling.md).
- ~~`POST /api/level` let any authenticated user mint arbitrary XP for themselves~~ ([#74](https://github.com/prashant-singh-2001/gamified_tracker/issues/74)) — fixed: gated `hasRole("ADMIN")` at the Gateway, capped at 10,000 XP per call, and every call now writes a `manual_xp_award` audit row. See the Level Tracker sections above and [Concurrency-Safe XP Accumulation](docs/features/concurrency-safe-xp.md).
- ~~Every downstream error reached the client as an empty `403`, regardless of its real status~~ ([#95](https://github.com/prashant-singh-2001/gamified_tracker/issues/95)) — fixed. The Gateway's `SecurityConfig` re-evaluates `.anyRequest().authenticated()` against the servlet container's *internal* `ERROR` dispatch to `/error` (Spring Boot's `spring.security.filter.dispatcher-types` includes `ERROR` by default); with no matcher permitting that dispatch, the context is anonymous and Spring Security's own entry point wrote a `401`/`403` over the real status and body. Fixed with `.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()` placed first in the matcher chain, restoring the byte-for-byte pass-through documented above.
- ~~`activityName` resolved by exact string match only — a typo was a bare `404` with no suggestion~~ ([#66](https://github.com/prashant-singh-2001/gamified_tracker/issues/66)) — implemented: a miss now returns ranked `suggestions`, and a confident, unambiguous match against an active activity is substituted automatically (`nameResolution` on the response). See the `POST /api/activitylog` section above and [Fuzzy Activity-Name Matching](docs/features/fuzzy-activity-matching.md).

Remaining non-issues, documented for awareness rather than as defects: `createdAt` is always server-set (client-supplied values on create endpoints are accepted but ignored); **reads are intentionally open** — any authenticated user can view any other user's activity logs and level/XP stats (`GET .../{id}`, `GET .../user/{id}`) as a deliberate social/leaderboard-style feature, not an access-control gap; **`bonusApplied`/`bonusMultiplier` are only ever real on the specific `POST` response that computed them** — every `GET` endpoint that returns an `ActivityLogResponse` hardcodes them to `false`/`1.0`, since they're not persisted, only computed in-memory at creation time; and **`leveledUp` is now `false` everywhere except the `POST /api/level`/`POST /level` response that actually crossed a threshold** (including on `POST /api/activitylog`/`POST /activitylog/`, since that write no longer applies XP synchronously — see Event-Driven Decoupling above). Direct calls to `:8081`/`:8082` bypassing the Gateway are also unauthenticated, since neither service has its own security layer — the `userId` header (and, for `POST /level`, the admin role check) is only trustworthy when it arrives via the Gateway.
