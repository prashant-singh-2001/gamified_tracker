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

`SecurityConfig` configures the OAuth2 Resource Server, validates JWTs via the configured `JwtDecoder`, and uses a `JwtAuthenticationConverter` to derive Spring Security authorities from the token's `role` claim (`ROLE_USER` / `ROLE_ADMIN`). Admin-only routes are enforced **at the URL level**, e.g. `.requestMatchers(HttpMethod.POST, "/api/activity", "/api/activity/").hasRole("ADMIN")` — ten such matchers exist today (`POST`, `PATCH` and `DELETE` on `/api/activity`, `GET /api/activity?includeInactive`, `/api/activitylog/review/**`, `POST /api/level`, `POST /api/threshold`, `POST /api/ranks/recompute`, `GET /api/level`, `GET /api/level/activity/**`). An `ADMIN` token succeeds on all of them; any other role receives `403 Forbidden`. **The only way to obtain an `ADMIN` token is via an out-of-band-provisioned account** — `POST /auth/register` always assigns `Role.USER` regardless of what the client sends (see `AdminBootstrap` under Auth below).

**Per-resource ownership on reads (#76–80, #88) is a separate, second layer**, enforced downstream in `activity-service`/`gamification-service` against the same trusted `userId` header rather than at the Gateway — see the "owner-only" notes on individual endpoints below.

**Caller identity on writes:** the JWT also carries a `userId` claim (the numeric `User.id`, set at register/login). `UserIdHeaderFilter` reads it and injects a trusted `userId` HTTP header on the request before it is routed downstream — overwriting/normalizing any `userId` header the client sent, so it can't be spoofed. `POST /api/activitylog` derives the acting user entirely from this trusted header; `POST /api/level` uses it to identify the **acting admin** (for the audit trail) while the XP **target** user is a separate, explicit field in the body (see that endpoint below) — neither body ever accepts a raw `userId` field.

---

## API Gateway (port 8080) — public surface

### Auth

#### `POST /auth/register`
Creates a user and returns an access/refresh token pair. Public (no token required).

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | must be unique. Trimmed and lower-cased before it is checked or stored, so `Ada@Example.com ` and `ada@example.com` are the same account |
| `password` | String | hashed with BCrypt before storage |

**No `role` field** — every account created here is `Role.USER`, unconditionally (issue #74; a client-supplied `role` used to be honored, letting any unauthenticated caller self-promote to `ADMIN` on this `permitAll` endpoint). An `ADMIN` account can only be provisioned out-of-band via `AdminBootstrap`, an `ApplicationRunner` gated by `app.admin.bootstrap.enabled` (off by default — see `.env.example`), which creates or promotes the configured email at startup.

**Response:** `200 OK`, JSON `AuthResponse`:
| Field | Type | Notes |
|---|---|---|
| `accessToken` | String | short-lived JWT — `role`/`userId` claims, send as `Authorization: Bearer <accessToken>` |
| `refreshToken` | String | opaque, longer-lived; exchange via `POST /auth/refresh` below rather than re-authenticating |

**`409 Conflict`** — `ProblemDetail` with `"detail": "Email already registered"` when the (normalised) email already belongs to an account. This also covers two registrations racing on the same address: exactly one succeeds and the rest get this `409`, never a `500`.
```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Email already registered", "instance": "/auth/register" }
```
Note this does disclose that an address is registered; `/auth/**` is rate limited (`429`) to bound enumeration.

---

#### `POST /auth/login`
Authenticates and returns an access/refresh token pair. Public (no token required).

**Request body:**
| Field | Type |
|---|---|
| `email` | String (trimmed and compared case-insensitively, matching how registration stores it) |
| `password` | String |

**Response:** `200 OK`, JSON `AuthResponse` (same shape as register). `401` `ProblemDetail` (`"Invalid email or password"`) if the user doesn't exist or the password doesn't match — the same message either way, so the error doesn't reveal which one failed.

---

#### `POST /auth/refresh`
Exchanges a refresh token for a new access/refresh token pair — rotation with reuse detection. Public (the refresh token itself is the credential; no bearer token needed).

**Request body:**
| Field | Type |
|---|---|
| `refreshToken` | String |

**Response:** `200 OK`, JSON `AuthResponse` with a **new** access token and a **new** refresh token — the old refresh token is consumed by this call and can never be exchanged again. `401` `ProblemDetail` on any of four distinct failures: token not found, already revoked, expired, or **already used** — the last one atomically detects reuse (`UPDATE ... WHERE isUsed = false`, so two concurrent replays can't both win) and revokes every other refresh token issued to that user, since reuse of a consumed token implies it was compromised. See [Authentication & Identity § Refresh tokens](docs/features/authentication-and-identity.md) for the full rotation/reuse-detection design.


---

#### `POST /auth/forgot`
Starts a password reset. Public, and under the same `/auth/**` rate limit (`429` when exceeded).

```json
// request
{ "email": "ada@example.com" }
// 202 Accepted, always the same body
{ "message": "If an account exists for that email, a reset link has been sent." }
```
The response is identical whether or not the address has an account, so it cannot be used to find out who is registered. The email is trimmed and lower-cased like everywhere else. A malformed or missing email is `400`. Asking again retires the earlier token, so only the latest one works.

Delivery goes through the `PasswordResetNotifier` interface. **No mail provider is wired yet**: the default implementation only logs that a reset was issued. For local development set `PASSWORD_RESET_LOG_TOKEN=true` to have it log the token (never do this in a shared environment). To send real mail, register a `PasswordResetNotifier` bean; the default backs off.

| Setting | Default | |
|---|---|---|
| `PASSWORD_RESET_TOKEN_TTL` | `30m` | how long a token works |
| `PASSWORD_RESET_LOG_TOKEN` | `false` | dev only, logs the raw token |

#### `POST /auth/reset`
Sets a new password using the token from the reset link. Public, rate limited with the rest of `/auth/**`.

```json
// request
{ "token": "<token from the link>", "newPassword": "a-new-password" }
// 204 No Content
```
`400` with `{"detail": "Invalid or expired reset token"}` for a token that is unknown, expired or already used (one message for all three). Tokens are single use, even under concurrent requests. Tokens are stored only as a SHA-256 hash.

On success every refresh token of the account is revoked, so other sessions cannot renew. An access token already issued stays valid until it expires. The new password is held to the same rule as registration (non-blank).
---

### Users

#### `GET /users/me`
The caller's own account, so a client can show their name without having stashed it at registration (a user who registered in another browser or session otherwise has nothing but a token). Served by the gateway itself, not proxied. Requires a token; `401` without one.

**Response:** `200 OK`
```json
{ "id": 20, "firstName": "Ava", "lastName": "Grinder", "role": "USER" }
```
Never includes the email or password hash. `404` `ProblemDetail` (`"User not found"`) for a valid token whose account has since been deleted.

---

### Activity

#### `GET /api/activity`
List all **active** activities. Requires auth (any role). A soft-deleted (`active: false`)
activity is never included (issue #84) — the catalog listing only ever shows the same set an
authenticated user could actually log time against.

**Response:** `200 OK`, JSON array of:
| Field | Type |
|---|---|
| `id` | long — the activity's id; use it with `GET /api/activity/{id}` |
| `name` | String |
| `category` | enum: `STUDY`, `WORK`, `GAMING`, `CHORES`, `HEALTH`, `OTHER` |
| `xpMultiplier` | double — the **effective** multiplier (per-activity override, else the category base) |
| `active` | boolean — always `true` here, since inactive activities are filtered out entirely |
| `description` | String |
| `createdAt` | ISO-8601 datetime string |

---

#### `GET /api/activity/{idOrName}`
Fetch one active activity by **id** or by **name**. Requires auth (any role).

- A segment of **digits only** (`/api/activity/5`) is an id: the activity with that id is returned. If there is no active activity with that id, the same digits are tried as a **name**, so an activity actually named `2048` is still reachable at `/api/activity/2048`. (If the digits are one activity's id and another's name, the id wins.)
- Anything else (`/api/activity/Deep%20Work`) is a name, exactly as before.

- `200 OK` — same shape as above (single object)
- `404` `ProblemDetail` — not found **or soft-deleted** (issue #84). The two are deliberately
  indistinguishable from this response alone — an inactive activity is logically gone, not merely
  hidden, so this endpoint never leaks whether a matching-but-disabled row exists. Contrast with
  `POST /api/activitylog` below, which *does* distinguish the two (`409` vs `404`) because it needs
  to tell a caller "re-enable it" rather than "create it."

---

#### `POST /api/activity`
Create an activity. **Requires `ADMIN` role** — a non-admin token gets `403`.

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `name` | String | should be unique (enforced at the DB level) |
| `category` | enum: `STUDY`\|`WORK`\|`GAMING`\|`CHORES`\|`HEALTH`\|`OTHER` | |
| `xpMultiplier` | double | **optional per-activity override.** `≤ 0` or omitted → the activity's `Category` base multiplier applies (`STUDY`/`WORK` 1.5, `HEALTH` 1.3, `OTHER` 1.0, `CHORES` 0.8, `GAMING` 0.5). A positive value overrides that base. e.g. `1.5` |
| `active` | boolean | optional, **defaults to `true` if omitted** (issue #84) — an activity created with `active: false` is invisible to both catalog reads above until an admin re-enables it with `PATCH` (found via `?includeInactive=true`) |
| `description` | String | optional |
| `createdAt` | ISO-8601 datetime string | accepted but **ignored** — the server always sets `createdAt` to the current time |

**Response:** `200 OK`, same shape as `GET /api/activity/{idOrName}` (including the new activity's `id`). Note `xpMultiplier` in the response is the **effective** multiplier (the override, or the resolved category base when none was set) — so an activity created without an explicit multiplier reports its category base rather than `0.0`.

**Errors:** `409` if an activity with that name already exists (`{"status":409,"detail":"An activity named 'Reading' already exists"}`).

#### `GET /api/activity?includeInactive=true` and `GET /api/activity/{idOrName}?includeInactive=true`
**`ADMIN` only** — the presence of the parameter, with any value, is gated (a non-admin gets `403`), because the
backend would otherwise bind `on`/`yes`/`1` to `true`. Same response shapes as above, but soft-deleted
(`active: false`) activities are included, ordered by `id`. It is how an admin finds a deactivated
activity to re-enable it.

#### `PATCH /api/activity/{id}`
Partially update an activity, including the `active` toggle. **Requires `ADMIN`.** Every field is optional,
an absent field is left alone, and at least one must be present.

```json
{ "name": "Deep Work", "category": "WORK", "xpMultiplier": 1.8, "description": "Focused block", "active": true }
```

| Field | Notes |
|---|---|
| `name` | trimmed; non-blank; max 255; must stay unique |
| `category` | `STUDY`\|`WORK`\|`GAMING`\|`CHORES`\|`HEALTH`\|`OTHER` |
| `xpMultiplier` | `>= 0`; **`0` clears the override** so the category base applies again; negative is `400` |
| `description` | an empty string clears it |
| `active` | `false` hides the activity from catalog reads and makes new logs against it `409`; `true` brings it back |

**Response:** `200 OK`, the updated activity (same shape as `GET /api/activity/{idOrName}`).
**Errors:** `400` empty body or invalid field; `404` unknown id (inactive ids are found); `409` the new name belongs to another activity (renaming to the activity's own name is fine).

Renames and category/multiplier changes are not retroactive to **XP already awarded**, but analytics group by the
activity's *current* name and category, so history re-buckets.

#### `DELETE /api/activity/{id}`
**Requires `ADMIN`.**

- If any activity log or streak references the activity, it is only **deactivated** (soft delete): history keeps
  its activity and the name stays reserved. Repeating the call is harmless.
- If nothing references it, the row is **removed**, and the name can be created again — this is how a mistaken
  create is undone.

**Response:** `200 OK`
```json
{ "id": 7, "name": "Deep Work", "result": "DEACTIVATED" }
```
`result` is `DEACTIVATED` or `REMOVED`. `404` for an unknown id. Per-activity level thresholds in
gamification-service are keyed by this id and are not touched by a hard delete.

---

### Activity Log

#### `GET /api/activitylog/{id}`
Fetch one activity log by its numeric id. Requires auth. **Owner-only (#78/#88).** The log's owner is compared against the caller's `userId` header; a log belonging to someone else 404s exactly like a nonexistent id — a `403` here would confirm the id exists to someone with no business knowing that. (This endpoint was previously documented as an intentional open read across users; that was the still-live #78 vulnerability, not a real design decision — it's fixed now.)

**Response:** `200 OK` (shape below) or `404` if not found *or owned by someone else*. `bonusApplied`/`bonusMultiplier`/`leveledUp` are always defaulted here, not the real historical values — see the note under `GET /api/activitylog/user/{id}` below.

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
| `leveledUp` | boolean | **Always `false` on this response.** XP is now applied asynchronously by the Gamification Service's RabbitMQ consumer, so whether this session leveled the user up isn't known yet at write time. Poll [`GET /api/activitylog/{id}/outcome`](#get-apiactivitylogidoutcome) (this response's `id`) for the real value. |
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

#### `POST /api/activitylog/natural`
Issue #70. Turns one free-text sentence ("studied Spring Boot for 90 minutes this morning") into a **preview** draft of the request above — **writes nothing**. Requires auth; `userId` comes from the trusted header, same as `POST /api/activitylog` (there's no per-user data to look up today, but the endpoint stays consistent with the rest of this controller rather than becoming the one path-variable exception). The client commits by `POST`ing the returned `draft` to `POST /api/activitylog` above, unchanged.

**The model classifies, Java computes** — same principle as the [AI Weekly Coaching Digest](docs/features/ai-weekly-digest.md)'s "the model narrates, it never computes": the LLM never emits a timestamp, only relative components (`dayOffset`, an optional clock time or a coarse time-of-day bucket, a stated duration). A pure Java class turns those into concrete times against a real clock, guaranteeing they can never land in the future.

**Request body:**
| Field | Type | Notes |
|---|---|---|
| `text` | String | required — one free-text sentence describing something already done |

**Response:** `200 OK`, always — same always-200-plus-status contract as `GET /api/insights/weekly`:
| Field | Type | Notes |
|---|---|---|
| `draft` | object \| null | an `ActivityLogRequest` (same shape as `POST /api/activitylog`'s body) — non-null only when `status` is `PARSED`. `activityName` is left as the raw text the model produced, **not** pre-resolved onto a catalog entry — see `nameResolution` below |
| `interpretation` | string \| null | a short human-readable restatement of what was understood (`PARSED`) or why nothing could be committed (`NEEDS_CLARIFICATION`); `null` for `DISABLED`/`UNAVAILABLE` |
| `status` | String enum | `PARSED` (draft is ready to commit), `NEEDS_CLARIFICATION` (the model answered, but no duration was stated, the duration was over the session-integrity cap, or the sentence described something upcoming — never a guessed or silently-clamped value), `DISABLED` (feature flag off), `UNAVAILABLE` (flag on, backend failed or timed out) |
| `nameResolution` | object \| null | (#66) a **preview** of what `activityName` would resolve to on commit, `{ requestedName, resolvedName, score }` — populated the same way `POST /api/activitylog`'s 200 response populates it, without rewriting `draft.activityName` |
| `suggestions` | array | ranked alternatives when nothing resolved confidently enough for `nameResolution` — same shape as the `404` on `POST /api/activitylog` |

Has its own `natural-log.enabled` feature flag, but shares the same `spring.ai.model.chat` backend selector as the AI Weekly Coaching Digest — no separate backend to run. See [Natural-Language Activity Logging](docs/features/natural-language-logging.md).

---

#### `GET /api/activitylog/{id}/outcome`
What applying this log's XP did — typically ready about a second after the `POST`. This is how a client learns whether a log leveled the user up, since `POST`'s own `leveledUp` is always `false`. Served by the Gamification Service (the gateway routes exactly this path there; everything else under `/api/activitylog` goes to the Activity Service). Requires a token; the caller is identified by it, not by a parameter.

**Response:** always `200 OK` with the same five keys:
```json
{ "status": "APPLIED", "xpApplied": 144.0, "leveledUp": true, "newLevel": 2, "previousLevel": 1 }
```
```json
{ "status": "PENDING", "xpApplied": null, "leveledUp": null, "newLevel": null, "previousLevel": null }
```
| Field | Notes |
|---|---|
| `status` | `APPLIED` once gamification has applied the XP; otherwise `PENDING` |
| `xpApplied` | XP added by this log (equals the `POST` response's `xpEarned`) |
| `leveledUp` | `newLevel > previousLevel` |
| `newLevel`, `previousLevel` | the **activity's** level after/before this log — the same level a level-up notification reports, not the overall level. A user's first award has `previousLevel` 1 |

`PENDING` means *XP has not been applied* and covers: still in flight (poll again), **held for review** (`reviewStatus` `FLAGGED` on the `POST` response — it stays `PENDING` until a maintainer approves it, and forever if rejected, so check `reviewStatus` before polling), an id that **doesn't exist**, and an id that **isn't yours**. The last two are deliberately indistinguishable from a real pending log so the endpoint can't be used to probe other users' logs. `400` for a non-numeric id, `401` without a token. Logs applied before this endpoint existed have no outcome and read `PENDING`.

---

#### `GET /api/activitylog/user/{id}`
List all activity logs for a user. Requires auth. **Owner-only (#79/#88)** — `{id}` must equal the caller's `userId` header, or the response is `403` (the subject is already named in the URL, so a 403 leaks nothing a 404 would also require explaining). Same fix, same rationale as `GET /api/activitylog/streaks/user/{id}` below and the three `GET /api/activitylog/analytics/user/{userId}/*` endpoints under [Analytics](#analytics) — all four were unguarded before #88.

**Response:** `200 OK`, JSON array of the same shape as the `POST` response above — **except** `bonusApplied`, `bonusMultiplier` are always `false`/`1.0` here (and on `GET /api/activitylog/{id}`), regardless of what actually happened when the log was created. Those two fields aren't persisted columns; they're only populated on the `POST` response itself, from the in-memory roll. `leveledUp` is `false` everywhere, including on the `POST` response itself now — see the note above. `403` `ProblemDetail` if `{id}` isn't the caller's own.

##### Paging, sorting and filtering

With **none** of the parameters below, this returns every log as a bare array, exactly as above — that form is unchanged and stays until the frontend switches. Passing **any one** of them selects the paged form, which answers an envelope instead:

| Parameter | Default | Notes |
|---|---|---|
| `page` | `0` | zero-based; `< 0` is a `400`. A page past the end is `200` with empty `content` |
| `size` | `20` | `1`–`100`; anything else is a `400` (it bounds one response, ask for the next page for more) |
| `sort` | `startTime,desc` | `field` or `field,asc\|desc`. A bare field is ascending. Fields: `startTime`, `endTime`, `createdAt`, `xpEarned`, `durationMinutes` — anything else is a `400` |
| `activityId` | — | only logs for that activity. An unknown id is an empty page, not an error |
| `from`, `to` | — | ISO dates (`2026-10-01`), **inclusive** of both days, by log **start** date (the same calendar the analytics use). `from` after `to` is a `400` |

Filters combine with AND. Rows with an equal sort value are ordered by `id` in the same direction, so a row is never repeated or dropped at a page boundary.

```http
GET /api/activitylog/user/20?page=1&size=3&sort=xpEarned,desc&activityId=1&from=2026-10-01&to=2026-10-31
```
```json
{ "content": [ { "id": 1130, "activity": {...}, "xpEarned": 229.39, ... }, ... ],
  "page": 1, "size": 3, "totalElements": 22, "totalPages": 8, "sort": "xpEarned,desc" }
```
`content` rows have the same shape as the unpaged array. `totalElements` counts the **filtered** rows. `sort` echoes the order actually applied (including the default). Invalid values are `400` `ProblemDetail`s naming the parameter, e.g. `"size must be between 1 and 100 (got 0)"`; the owner-only `403` applies to the paged form too.

---

#### `GET /api/activitylog/user/{id}/recent`
Quick picks for the "log an activity" screen: the caller's **most-logged active activities**, most logged first. Requires auth. **Owner-only** — `{id}` must be the caller's own, else `403`, like the other per-user reads.

| Param | Default | Rules |
|---|---|---|
| `limit` | `5` | `1`–`20`, else a `400` `ProblemDetail` (`"limit must be between 1 and 20 (got 0)"`) |

**Response:** `200 OK`, JSON array (empty for a user with no logs):
```json
[ { "activityId": 1, "name": "Deep Work", "category": "WORK", "timesLogged": 61, "lastUsedAt": "2026-10-19T14:22:04" },
  { "activityId": 3, "name": "Algorithms Study", "category": "STUDY", "timesLogged": 45, "lastUsedAt": "2026-10-18T13:47:00" } ]
```
| Field | Notes |
|---|---|
| `activityId` | pairs with `GET /api/activity/{id}` |
| `timesLogged` | the caller's logs for it, **any review status** (a flagged session is still something they chose to log) |
| `lastUsedAt` | the **start** of their most recent session for it |

Ordered by `timesLogged` descending, then `lastUsedAt` descending, then `activityId`, so the order is total and doesn't reshuffle between calls. **Inactive activities are never included** however often they were logged: logging against one is a `409`, so offering it as a shortcut would just hand the user an error.

---

#### `GET /api/activitylog/streaks/user/{id}`
List every `(activity, streak)` pair for a user. Requires auth. **Owner-only (#80/#88)** — same rule and same rationale as `GET /api/activitylog/user/{id}` above.

**Response:** `200 OK`, JSON array:
| Field | Type | Notes |
|---|---|---|
| `activityId` | Long | |
| `currentStreak` | int | consecutive days logged, mutated in place — see [Streaks](docs/features/streaks.md) |
| `longestStreak` | int | high-water mark, never decreases |
| `lastActivityDate` | ISO-8601 date | |

`403` `ProblemDetail` if `{id}` isn't the caller's own.

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
| `GET` | `/api/level` | **ADMIN** | list every level-tracker row (all users, all activities). ADMIN-gated (#88) — every user's rows, no single subject to compare against the caller |
| `GET` | `/api/level/{id}` | authenticated, **owner-only (#77/#88)** | one row by internal id — `404` if missing *or owned by someone else* (a `403` would confirm the id exists to someone with no business knowing that) |
| `POST` | `/api/level` | **ADMIN** | manually award XP to an activity, recalculating level. See below — not a general-purpose write, and not what the normal activity-logging flow uses |
| `GET` | `/api/level/user/{userId}` | authenticated, **owner-only (#76/#88)** | all rows for a given user — `{userId}` must be the caller's own or the response is `403`. **This is where the real, eventual `leveledUp` outcome of a `POST /api/activitylog` becomes visible**, shortly after the async XP application completes |
| `GET` | `/api/level/activity/{activityId}` | **ADMIN** | all rows for a given activity, across every user. ADMIN-gated (#88), same reasoning as `GET /api/level` — `GET /api/leaderboard/activity/{id}` is the public per-activity view |

Two of these five are **self-service reads** — a caller only ever sees their own rows, enforced in
`gamification-service` against the trusted `userId` header, not at the Gateway (#76/#77/#88; these
were previously undocumented open reads across users — that was the still-live #76/#77
vulnerability, not a real design decision). The other two are genuinely cross-user (every row, or
every row for one activity, with no single subject to check) and are ADMIN-gated at the Gateway
instead, the same way `POST /api/level` already was.

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
| `GET` | `/api/activitylog/analytics/user/{userId}/category-summary` | authenticated, **owner-only (#88)** | totals per `Category`: `totalDurationMinutes`, `totalXpEarned`, `totalSessions`. `{userId}` must be the caller's own or `403` |
| `GET` | `/api/activitylog/analytics/user/{userId}/xp-over-time?days=7` | authenticated, **owner-only (#88)** | one `{date, totalXpEarned, totalDurationMinutes}` entry per day in the window, **zero-filled** — always returns exactly `days` entries regardless of how many have logs |
| `GET` | `/api/activitylog/analytics/user/{userId}/weekly-report[?mode=calendar]` | authenticated, **owner-only (#88)** | `currentWeekXp`, `previousWeekXp`, `percentageChange` (`100.0` if the previous week was `0` and this week isn't, `0.0` if both are `0`), `totalActiveMinutes`, `topCategory` (`null` if the week has no logs), `dailyBreakdown` (7 zero-filled entries), plus `mode` and the windows used — see [Weekly report modes](#weekly-report-modes) |
| `GET` | `/api/activitylog/analytics/user/{userId}/best-time-of-day` | authenticated, **owner-only** | issue #72 — "analytics, not ML": a `GROUP BY hour_of_day` over all-time logs, not a rolling window. See below |

The first three were previously open reads (`{userId}` could be anyone) — fixed alongside #76–80.
`best-time-of-day` shipped after that fix and reuses the same guard from day one.

#### Weekly report modes

`weekly-report` takes an optional `?mode=`, case-insensitive:

| `mode` | The "week" is | Default? |
|---|---|---|
| `rolling` | the **last seven days ending today** (today−6 … today), against the seven before them | **yes** — what you get with no parameter, exactly as before |
| `calendar` | the **Monday-to-Sunday** week containing today, against the same weekdays of the previous week | no |

An unknown value is a `400` `ProblemDetail` (`"mode must be one of: rolling, calendar (got 'monthly')"`) — never a silent fallback. Days are bucketed by log **start date** in the server's calendar (the services run on UTC), unchanged.

**Both modes compare two windows of the same length.** A calendar report on a Tuesday would otherwise compare "this week so far" (two days) with a *full* previous week and read as a slump every Monday-to-Saturday. So on Tuesday it compares Mon–Tue with last week's Mon–Tue. On Sunday that is the whole week against the whole previous week. The response says exactly which days it used:

```json
{ "currentWeekXp": 200.0, "previousWeekXp": 100.0, "percentageChange": 100.0,
  "totalActiveMinutes": 135, "topCategory": "STUDY",
  "dailyBreakdown": [ {"date":"2026-01-05",...}, ... 7 entries, Monday..Sunday ... ],
  "mode": "CALENDAR",
  "periodStart": "2026-01-05", "periodEnd": "2026-01-11",
  "previousPeriodStart": "2025-12-29", "previousPeriodEnd": "2025-12-31" }
```
| New field | Meaning |
|---|---|
| `mode` | `ROLLING` or `CALENDAR` |
| `periodStart` / `periodEnd` | first and last day **shown**: rolling is today−6…today; calendar is Monday…Sunday, so days after today are present in `dailyBreakdown` with zero XP and a client can draw all seven columns |
| `previousPeriodStart` / `previousPeriodEnd` | the window `previousWeekXp` covers (equal in length to the current window up to today) |

`dailyBreakdown` always has exactly seven entries in both modes. The AI digest (`/api/insights/weekly`) delegates to the **rolling** report and is unchanged.

**`GET .../best-time-of-day` response:**
| Field | Type | Notes |
|---|---|---|
| `hourlyBreakdown` | array | 24 entries, one per hour `0`–`23`, **zero-filled** (same convention as `xp-over-time`) — each `{hour, totalDurationMinutes, totalXpEarned, totalSessions}` |
| `bestHour` | Integer \| null | the hour with the most XP across all categories combined; `null` if the user has no XP at all (no logs, or logs that all earn `0.0` XP) |
| `bestCategory` | String enum \| null | resolved the same way `weekly-report`'s `topCategory` is (`groupingBy` + `summingDouble` + `max`), over all-time logs instead of one week |
| `bestCategoryHour` | Integer \| null | the peak hour **within** `bestCategory` only, scoped to that category's own logs — the "hour Y" half of "you log the most XP in category X around hour Y" |

`bestHour`, `bestCategory`, and `bestCategoryHour` are **null as a set, never independently** —
`bestCategory`/`bestCategoryHour` are only computed when `bestHour` is non-null, so a response never
pairs a `null` `bestHour` with a non-null `bestCategory`.

---

### Insights

AI-generated weekly coaching digest (issue #65), routed via the same `/api/insights/**` addition to
the Activity Log route bean — no new rate-limit bucket. See [AI Weekly Coaching
Digest](docs/features/ai-weekly-digest.md) for the full design, including the always-`200` contract
and the two interchangeable local model backends.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/insights/weekly` | authenticated | `weekStart`, `weekEnd`, `totals` (same shape as the weekly-report above, delegated — never recomputed), `categories` (per-category totals **for the week**, unlike the all-time `category-summary` above), `narrative` (`string \| null`), `narrativeStatus` (`GENERATED` \| `DISABLED` \| `UNAVAILABLE` — always present, so the client never needs a second response shape or a non-200 status to tell "off" from "the model failed"). `userId` comes from the trusted JWT-derived header, never a path segment — this response is built from the caller's own private notes |

---

### Notifications

Surfaces level-up events (`LevelUpEvent`) as a caller-scoped feed. Full detail in
[Level-Up Notifications](docs/features/level-up-notifications.md).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/notifications?unreadOnly=false` | authenticated | the caller's own level-up events, newest first. `unreadOnly=true` filters to unread only |
| `GET` | `/api/notifications/unread-count` | authenticated | `{"count": <long>}`. The old capital-C `Count` key has been removed |
| `POST` | `/api/notifications/{id}/read` | authenticated | marks one notification read. `204 No Content`, or `404` if `{id}` doesn't belong to the caller (ownership is enforced — this is not an open read like Level Tracker/Activity Log) |
| `POST` | `/api/notifications/read-all` | authenticated | marks **every** unread notification of the caller read, in one call. `200 OK` with `{"updated": <int>}` — how many it flipped. **Idempotent**: with nothing unread it answers `{"updated": 0}`. Only ever touches the caller's own rows (the user comes from the trusted header; there is no parameter to name another) |

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

**Response shape** (paged endpoints): array of `{rank, userId, totalXp, displayName}`. `displayName` is the user's first name plus last initial (`"Ada L."`) — see [Display names](#display-names) below.

#### Display names

Rows on `GET /api/leaderboard`, `GET /api/leaderboard/activity/{id}`, `GET /api/ranks/{tier}/leaderboard` and `GET /api/ranks/me/leaderboard` carry a **`displayName`**: the user's first name plus the initial of their last name (`"Ada Lovelace"` → `"Ada L."`; just `"Ada"` if there is no last name). Never the email and never the full last name.

```json
[ { "rank": 1, "userId": 20, "totalXp": 14216.63, "displayName": "Ava G." },
  { "rank": 2, "userId": 22, "totalXp": 6344.37,  "displayName": "Chloe O." } ]
```
`displayName` is `null` (the key is always present) for an account that no longer exists, so a client can fall back to `"Climber #<userId>"`. The gateway adds it to the response — gamification-service never sees names — with one batched lookup per page. Only `2xx` array responses are rewritten; errors pass through untouched. `GET /api/leaderboard/me` (a bare number) and `GET /api/ranks/me` (a single card) are unchanged.

> **Known issue, not caused by this:** `GET /api/leaderboard/activity/{id}` currently returns `500` on a real Postgres — its query selects `total_xp` while grouping by user (`column "total_xp" must appear in the GROUP BY clause`). The `displayName` enrichment is wired for it, but it can't be seen working until that query is fixed.

---

### Ranks

A separate, materialized ranking system from Leaderboard above — percentile-based tiers
(`SUMMIT`…`BASECAMP`) recomputed on a schedule into `user_rank`, so reads here are O(1) rather than
a live aggregate query. See [Rank & Level System](docs/features/rank-and-level-system.md).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/ranks/me[?allowUnranked=true]` | authenticated | the caller's `RankCardDto`: `tier`, `overallLevel`, `totalXp`, `percentile`, `position`, `totalUsers`, `updatedAt`, plus the [rank nudge](#rank-nudge) fields. `404` if the caller has no rank yet (never recomputed) — **unless** `allowUnranked=true`, which answers `200 {"ranked": false}` instead |
| `GET` | `/api/ranks/{tier}/leaderboard?page=0&size=20` | authenticated | members of one tier (`SUMMIT`, `PEAK`, `RIDGE`, `ALPINE`, `ASCENT`, `HIGHLAND`, `FOOTHILL`, `TRAILHEAD`, `BASECAMP`), each `{withinRankPosition, userId, totalXp, overallLevel, tier, displayName}` |
| `GET` | `/api/ranks/me/leaderboard?page=0&size=20` | authenticated | same shape, scoped to the caller's own tier. `404` if the caller has no rank yet |
| `GET` | `/api/ranks` | authenticated | tier distribution: array of `{tier, userCount}` |
| `POST` | `/api/ranks/recompute` | authenticated | forces an off-schedule recompute (normally runs every `ranking.recompute-interval-ms`, default 5 min). Returns `{"rankedUsers": <int>}`. **No admin guard** — any authenticated caller can trigger this |

#### Rank nudge

`GET /api/ranks/me` also says how far the caller is from the next tier, so a result screen can show "1,500 XP to PEAK":

```json
{ "tier": "RIDGE", "overallLevel": 6, "totalXp": 3851.57, "percentile": 0.2222, "position": 3, "totalUsers": 9,
  "updatedAt": "2026-10-20T04:12:01.2", "ranked": true,
  "tierCutoffXp": 3851.57, "nextTier": "PEAK", "xpToNextTier": 2492.80 }
```
| Field | Notes |
|---|---|
| `ranked` | always `true` on a card |
| `tierCutoffXp` | the lowest XP currently held by anyone in the caller's tier (the effective floor for staying in it) |
| `nextTier` | the next **better** tier that currently has members — a tier nobody occupies can't be reached by earning XP, so it is skipped. `null` at `SUMMIT` (or if no better tier is populated) |
| `xpToNextTier` | XP needed to match the lowest member of `nextTier`; never negative; `null` when `nextTier` is `null` |

These come from the same snapshot as the rest of the card, so they are at most one recompute (5 min) stale, and approximate by nature: tiers are assigned by **relative position** (a top-fraction percentile), so as other users earn XP every boundary moves. `percentile` is unchanged: a **top** fraction where `0` is the best.

**Unranked callers.** By default a caller who hasn't been ranked yet still gets `404` (clients depend on it). Passing `?allowUnranked=true` opts in to `200 {"ranked": false}` instead, so a client need not treat a `404` as "not an error". The default will only be flipped once the frontend has moved.

---

## Activity Service (port 8081) — internal

Base path `/activity` and `/activitylog`. No auth layer of its own — auth is only enforced at the Gateway.

#### `GET /activity/`
List all activities. Same response shape as the Gateway's `GET /api/activity`.

#### `GET /activity/{idOrName}`
Fetch one activity by id or name (digits only = an id, with a fallback to the same digits as a name; see the Gateway section above). `200 OK` with the activity, or `404` `ProblemDetail` (`"Activity not found: {idOrName}"`) if missing.

#### `POST /activity/`
Create an activity. Same request/response shape as the Gateway's `POST /api/activity` (no role check at this layer — that's Gateway-only).

#### `GET /activitylog/{id}`
Fetch one activity log by id. Reads the caller's id from the required `userId` request header (same trust-boundary caveat as `POST /activitylog/` below when called directly against `:8081`) and compares it against the log's owner — **`404`** either way if it doesn't exist or belongs to someone else (**#78/#88**), else `200 OK`.

#### `POST /activitylog/`
Create an activity log (computes duration + XP bonus, saves the log, and writes an outbox row for async XP application — see [`EVENT_DRIVEN_DECOUPLING.md`](docs/features/event-driven-decoupling.md); no synchronous call to Gamification Service). Same request/response shape as the Gateway's `POST /api/activitylog`, including the now-always-`false` `leveledUp`. `404` `ProblemDetail` if `activityName` doesn't match an existing activity. Reads `userId` from the `userId` request header (required) rather than the body — when called through the Gateway this header is the trusted, JWT-derived value; called directly against `:8081` (bypassing the Gateway, as this dev setup allows), the header is unauthenticated and effectively caller-supplied, since this service has no security layer of its own.

#### `GET /activitylog/user/{id}`
List all activity logs for a user. **Owner-only (#79/#88)** — `{id}` must equal the caller's `userId` header (required) or the response is `403`, same trust-boundary caveat as `POST /activitylog/` above when called directly against `:8081`. Accepts `page`, `size`, `sort`, `activityId`, `from` and `to`; with none of them the response is the bare array, with any of them the paged envelope — see [Paging, sorting and filtering](#paging-sorting-and-filtering) above.

#### `GET /activitylog/streaks/user/{id}`
List every `(activityId, currentStreak, longestStreak, lastActivityDate)` row for a user. **Owner-only (#80/#88)**, same rule and trust-boundary caveat as `GET /activitylog/user/{id}` above.

#### `POST /activitylog/natural`
Issue #70. Parses one free-text sentence into a draft `ActivityLogRequest` — **writes nothing**, same request/response shape as the Gateway's `POST /api/activitylog/natural`. Reads `userId` from the `userId` request header (required), same trust-boundary caveat as `POST /activitylog/` above when called directly against `:8081`.

### Analytics

All four below are **owner-only** (the first three since #88, `best-time-of-day` from day one) —
each reads the caller's id from the required `userId` request header and rejects a `{userId}`
mismatch with `403`, same trust-boundary caveat as `POST /activitylog/` above when called directly
against `:8081`. The first three were previously unguarded — any caller could read any `{userId}`'s
analytics.

#### `GET /activitylog/analytics/user/{userId}/category-summary`
Aggregates activity logs for a user grouped by category (`STUDY`, `WORK`, `GAMING`, `CHORES`, `HEALTH`, `OTHER`). Returns JSON array containing `category`, `totalDurationMinutes`, `totalXpEarned`, and `totalSessions`.

#### `GET /activitylog/analytics/user/{userId}/xp-over-time?days=7`
Calculates daily XP earned and total active minutes for the specified window (default 7 days). Returns JSON array of daily breakdown objects.

#### `GET /activitylog/analytics/user/{userId}/weekly-report`
Generates a comprehensive weekly report comparing current week vs previous week XP, percentage change, total active minutes, top category, and a 7-day daily breakdown. Accepts `?mode=rolling` (default, the last seven days) or `?mode=calendar` (Monday to Sunday) -- see [Weekly report modes](#weekly-report-modes). Both compare equal-length windows and the response names them (`periodStart`, `periodEnd`, `previousPeriodStart`, `previousPeriodEnd`).

#### `GET /activitylog/analytics/user/{userId}/best-time-of-day`
Issue #72. Groups all-time logs by hour of day (`0`–`23`, zero-filled). Returns `hourlyBreakdown` plus three derived fields — `bestHour`, `bestCategory`, `bestCategoryHour` — same shape as the Gateway's `GET /api/activitylog/analytics/user/{userId}/best-time-of-day` above.

### AI Weekly Coaching Digest

#### `GET /insights/weekly`
Reads `userId` from the trusted request header (see `POST /activitylog/` above for the same
trust-boundary caveat when called directly against `:8081`). Delegates the headline totals to
`AnalyticsService.getWeeklyReport()`, computes a per-category-**for-the-week** breakdown that doesn't
exist anywhere else, and — if `insights.enabled=true` and a model backend is configured — asks a
local Ollama or Docker Model Runner instance to narrate those numbers. Always `200`: a disabled
feature, an unconfigured backend, and a live model timeout all return the same shape with
`narrative: null` and a `narrativeStatus` explaining why. Full design: [AI Weekly Coaching
Digest](docs/features/ai-weekly-digest.md).

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
List every `LevelTracker` row (all users, all activities). **ADMIN-gated at the Gateway only (#88)** — this service has no security layer of its own, so hitting `:8082` directly bypasses that gate entirely, same caveat as everywhere else in this section.

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
Fetch one `LevelTracker` by its internal numeric id. Reads the caller's id from the required `userId` request header (trustworthy through the Gateway; caller-supplied if hit directly on `:8082`, same caveat as `POST /level` below) and compares it against the row's owner — **`404`** either way if it doesn't exist or belongs to someone else (**#77/#88**; a `403` would confirm the id exists to someone with no business knowing that), else `200 OK` with the shape above.

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
List all `LevelTracker` rows for a given user (one per activity they've logged). **Owner-only (#76/#88)** — reads the caller's id from the required `userId` request header and rejects a mismatch with `403` (same trust caveat as `POST /level`: through the Gateway the header is the JWT's real subject; hit directly on `:8082` a caller supplies it themselves, so this ownership check only means something once the Gateway is the actual entry point). Was previously an open read — any caller could pass any `{userId}` with no check at all.

#### `GET /level/activity/{activityId}`
List all `LevelTracker` rows for a given activity (one per user who's logged it). **ADMIN-gated at the Gateway only (#88)**, same reasoning as `GET /level` above — every user's row for this activity, no single subject to check.

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

`401` (`POST /auth/login` failures) and `400` (validation failures, on every service) also use `ProblemDetail`.

**Validation `400`s** (a `@Valid` request body that fails Bean Validation — gateway auth endpoints, activity-service and gamification-service alike) carry every violation twice: joined into `detail` for a human, and as an **`errors`** extension member mapping each offending field to its message, so a form can mark the exact field:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "email should be formatted and required; password is required",
  "instance": "/auth/register",
  "errors": {
    "email": "email should be formatted and required",
    "password": "password is required"
  }
}
```
`errors` keys are the request field names (nested fields use their path, e.g. `items[0].name`); if a field fails several constraints its messages are joined with `"; "`; a class-level constraint with no field is keyed by the object name. `detail` is sorted by field name, so it is stable. `detail` has always been the joined messages on activity-service and gamification-service, and the gateway now matches, so a client that only reads `detail` keeps working.

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
