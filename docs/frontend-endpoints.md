# Frontend endpoint reference

Every endpoint a frontend can call, taken from the controllers on `main` (fluid ranks, #136, included).
Everything goes through the **gateway** at `http://localhost:8080`. Downstream paths carry an `/api` prefix
(the gateway strips it); `/auth` and `/users` do not. Everything except `/auth/*` needs
`Authorization: Bearer <accessToken>`.

For exact request and response fields see [API.md](../API.md). Each service also publishes a generated spec
at `/v3/api-docs` (gateway `:8080` for `/auth/*` and `/users/me`; activity `:8081`; gamification `:8082`),
with error bodies and examples.

**Legend:** **ADMIN** means the caller's role must be ADMIN (otherwise `403`). "Own only" means the path or
resource must belong to the caller (a mismatch is `403`, or `404` for an id that is someone else's).

---

## Auth (public, rate limited to 10 requests per minute per IP)

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | `{firstName,lastName,email,password}` returns `{accessToken,refreshToken}`. `409` if the email exists. The role is always USER |
| POST | `/auth/login` | `{email,password}` returns the same pair. `401` "Invalid email or password" |
| POST | `/auth/refresh` | `{refreshToken}` returns a new pair. Refresh tokens are single use |
| POST | `/auth/forgot` | `{email}` always returns `202`, whether or not the account exists |
| POST | `/auth/reset` | `{token,newPassword}` returns `204`. `400` for an unknown, expired or used token |

## Account

| Method | Path | Notes |
|---|---|---|
| GET | `/users/me` | The caller's profile |

## Activities (catalog)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/activity` | Active activities, each with its `id` |
| GET | `/api/activity/{idOrName}` | Digits mean an id, falling back to the same digits as a name |
| POST | `/api/activity` | **ADMIN**. `409` on a duplicate name |
| PATCH | `/api/activity/{id}` | **ADMIN**. Edit name, category, xpMultiplier, description, or toggle `active` |
| DELETE | `/api/activity/{id}` | **ADMIN**. Soft delete if logs reference it, hard delete otherwise |
| GET | `/api/activity?includeInactive=true` | **ADMIN**. Includes deactivated activities |
| GET | `/api/activity/{idOrName}?includeInactive=true` | **ADMIN** |

## Activity logs

| Method | Path | Notes |
|---|---|---|
| POST | `/api/activitylog` | Log a session. `leveledUp` is always `false` here (XP is applied asynchronously) |
| GET | `/api/activitylog/{logId}/outcome` | `PENDING` or `APPLIED` with `xpApplied`, `leveledUp`, `newLevel`, `previousLevel` |
| POST | `/api/activitylog/natural` | Previews a log from one sentence; writes nothing. Always `200`; `status` is `PARSED`, `NEEDS_CLARIFICATION`, `DISABLED` (the default) or `UNAVAILABLE` |
| GET | `/api/activitylog/{id}` | Own only |
| GET | `/api/activitylog/user/{id}` | Own only. Unpaged; add `page`, `size`, `sort`, `activityId`, `from`, `to` for a paged, filtered envelope |
| GET | `/api/activitylog/user/{id}/recent?limit=5` | The caller's most-logged activities (quick picks). Own only |
| GET | `/api/activitylog/streaks/user/{id}` | Streaks per activity with a computed `active` flag. Own only |

## Analytics and insight (own `{userId}` only)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/activitylog/analytics/user/{userId}/category-summary` | |
| GET | `/api/activitylog/analytics/user/{userId}/xp-over-time?days=7` | |
| GET | `/api/activitylog/analytics/user/{userId}/weekly-report?mode=rolling\|calendar` | `rolling` is the default |
| GET | `/api/activitylog/analytics/user/{userId}/best-time-of-day` | |
| GET | `/api/insights/weekly` | Always `200`; `narrativeStatus` is `GENERATED`, `DISABLED` or `UNAVAILABLE` |

## Level and XP

| Method | Path | Notes |
|---|---|---|
| GET | `/api/level/{id}` | Own only (someone else's row is a `404`) |
| GET | `/api/level/user/{userId}` | Own only |
| GET | `/api/level` | **ADMIN**. Every user's rows |
| GET | `/api/level/activity/{activityId}` | **ADMIN** |
| POST | `/api/level` | **ADMIN**. Manual XP award, capped at 10,000 per call and audited |

## Thresholds (level curve)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/threshold` | List all |
| GET | `/api/threshold/activity/{activityId}?upToLevel=10` | The effective ladder: explicit rows, else the default curve |
| POST | `/api/threshold/activity` | A read despite the `POST`; the body is a composite key |
| POST | `/api/threshold` | **ADMIN**. Create or overwrite. `409` if cumulative XP stops rising with level |
| PUT | `/api/threshold/{activityId}/{level}` | **ADMIN**. Body `{"xpRequired": n}`. `404` if absent, `409` if it breaks the curve |
| DELETE | `/api/threshold/{activityId}/{level}` | **ADMIN**. `204` |

## Leaderboard (lifetime XP)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/leaderboard?page=0&size=20` | `page` and `size` are **required**. Rows include `displayName` |
| GET | `/api/leaderboard/activity/{activityId}?page&size` | Same shape |
| GET | `/api/leaderboard/me` | The caller's position |

## Ranks (recent form, divisions, movement)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/ranks/me` | `404` until the caller is ranked. Add `?allowUnranked=true` for `200 {"ranked": false}` instead |
| GET | `/api/ranks` | Users per tier: `[{tier, userCount}]` |
| GET | `/api/ranks/{TIER}/leaderboard?page=0&size=20` | Ordered by `rankScore`; rows include `displayName`, `division`, `positionChange`, `trend` |
| GET | `/api/ranks/me/leaderboard?page&size` | The caller's own tier. `404` if not ranked |
| POST | `/api/ranks/recompute` | **ADMIN**. Forces a recompute |

Tiers, best to worst: `SUMMIT`, `PEAK`, `RIDGE`, `ALPINE`, `ASCENT`, `HIGHLAND`, `FOOTHILL`, `TRAILHEAD`,
`BASECAMP`. Divisions run 1 (best) to 3. `percentile` is a **top fraction** (0 is best); `positionChange`
positive means up; `trend` is `NEW`, `PROMOTED`, `DEMOTED`, `UP`, `DOWN` or `SAME`.

## Notifications (level-ups)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/notifications?unreadOnly=false` | The caller's notifications |
| GET | `/api/notifications/unread-count` | `{"count": n}` |
| POST | `/api/notifications/{id}/read` | `204` |
| POST | `/api/notifications/read-all` | `{"updated": n}` |

## Admin review queue (session integrity)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/activitylog/review/flagged` | **ADMIN**. Logs held back by the outlier check |
| POST | `/api/activitylog/review/{id}/approve` | **ADMIN**. Releases the XP |
| POST | `/api/activitylog/review/{id}/reject` | **ADMIN** |

---

## Not meant for a frontend

- `/v3/api-docs` and `/swagger-ui.html`: API docs (the gateway serves `/auth/*` and `/users/me`; the other
  services serve their own on `:8081` and `:8082`).
- `/actuator/*`: health and metrics.
- Direct service ports `:8081` and `:8082`: they skip the gateway's admin checks, so do not call them from a
  browser.

## Conventions to handle

- **Identity** comes from the token. Never send a user id in a request body.
- **Errors** are RFC 7807 `ProblemDetail`. Validation `400`s also carry an `errors` map (field to message).
  An unknown activity name on `POST /api/activitylog` is a `404` with `requestedName` and ranked `suggestions`.
- **Rate limit:** `/auth/*` returns `429` with `{"error":"Too many requests"}`, which is not a ProblemDetail.
- **CORS** is closed by default; set `CORS_ALLOWED_ORIGINS` to your frontend's origin.
- **Timestamps** are UTC and zone-less (`2026-10-20T08:30:00`); send and read them as UTC.
