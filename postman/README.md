# Postman Collection — Gamified Tracker

`gamified-tracker.postman_collection.json` is a self-contained Postman collection (v2.1 schema) covering [API.md](../API.md)'s API Gateway public surface (port 8080) end to end. It also includes direct-hit folders for Activity Service (8081) and Gamification Service (8082), but those are a representative sample for internal debugging, not exhaustive — they don't have direct-hit equivalents of the Analytics/Notifications/Leaderboard/Ranks folders, only Activity/Activity Log and Level Tracker/Threshold.

## Import

Postman → **Import** → select `gamified-tracker.postman_collection.json`. No separate environment file needed — all variables (base URLs, tokens, generated test data) live on the collection itself.

## Run

1. Start the stack: `docker-compose up --build` (Gateway, Activity Service, Gamification Service, Eureka, Postgres all need to be up).
2. In Postman, open the collection and use **Run collection** (Collection Runner), keeping the default top-to-bottom folder order:
   `Auth → Activity → Activity Log → Analytics → Level Tracker → Notifications → Ranks → Leaderboard → Activity Level Threshold → Security - IDOR Verification → Rate Limiting → Activity Service (Internal) → Gamification Service (Internal)`.

   Order matters — later folders read variables (tokens, ids, the created activity name) captured by earlier ones. In particular: **Analytics** needs Activity Log's created log; **Notifications** forces its own XP grant large enough to cross a level (so there's always a real event to mark read, regardless of what earlier folders happened to do); **Ranks** and **Leaderboard** both need Level Tracker's XP grant to already be applied. A collection-level pre-request script generates fresh, unique test emails/activity names/ids the first time any request runs, so re-running the whole collection repeatedly won't collide with previous runs.
3. Individual requests can also be run standalone once the `Auth` folder has populated `userAToken` / `userBToken` / `adminToken` in the collection variables.

## Folders

| Folder | Covers |
|---|---|
| Auth | register (user + admin), login, invalid-password 401 |
| Activity | list/get/create, admin-only 403 enforcement |
| Activity Log | create, get by id, list by user, open-read behavior, 404 |
| Analytics | category summary, zero-filled XP-over-time timeline, weekly report |
| Level Tracker | create/update XP, list, get by user/activity, negative-XP 400 |
| Notifications | list (+ `unreadOnly`), unread count, mark-read (204), ownership enforcement |
| Ranks | not-yet-ranked 404, on-demand recompute, my rank, tier leaderboard, rank distribution |
| Leaderboard | global (page/size required — 400 if omitted), per-activity, `/me` (bare number response) |
| Activity Level Threshold | create, list, composite-key lookup, 404, effective-ladder curve fallback + explicit-rows-win precedence |
| **Security - IDOR Verification** | the userId-from-JWT fix: write-binds-to-caller, forged `userId` header is neutralized, reads stay open by design, unauthenticated requests are rejected |
| Rate Limiting | trips the Gateway's per-user 429 via a self-looping request (Collection Runner only) |
| Activity Service (Internal :8081) | a subset of the above hit directly, bypassing the Gateway |
| Gamification Service (Internal :8082) | same, for Level Tracker/Threshold only |

## Known caveats baked into the tests

- **`GET /level/{id}` has no reachable happy path.** `LevelTrackerDto` never exposes its internal numeric id in any response, so only the not-found case (`404`) is scriptable without direct DB access.
- **Reads are intentionally open.** `GET .../{id}` and `GET .../user/{id}` let any authenticated user view any other user's data — a social/leaderboard feature, not an access-control gap. Tests in the Security folder assert `200`, not `403`, when User A reads User B's data. The same applies to Analytics, which is path-scoped by `{userId}` rather than the trusted header.
- **Achievement badges have no HTTP surface yet.** `AchievementServiceImpl.evaluateAndAward` is implemented and unit-tested at the code level, but nothing calls it in production and there's no controller — so there's nothing to exercise here. See `docs/features/achievement-badges.md`.
- **`Notifications` grants XP as a setup step, not as the thing under test.** It POSTs 200 XP against `thresholdActivityId` specifically to guarantee a level-up (and therefore a `LevelUpEvent`) exists to list and mark read, since no earlier folder's XP grant reliably crosses a level on its own.
