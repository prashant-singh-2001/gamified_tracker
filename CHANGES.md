# Changes

## Fix: lost-update race in `LevelTrackerService.save()` (issue #5)

**Problem:** `save()` did an unguarded read-modify-write — read `totalXp`, add XP in Java, save — with no locking and no unique constraint on `(userId, activityId)`. Concurrent activity logs for the same user+activity could silently drop XP increments, and concurrent first-time writes could create duplicate `LevelTracker` rows. An in-progress fix (an `upsertXp` query + an activity-archive table) existed but didn't actually close the race and had its own bugs — see below.

**Fix:**

- **`dao/LevelTracker.java`** — added a unique constraint `uk_level_tracker_user_activity` on `(user_id, activity_id)`, preventing duplicate rows and giving the atomic insert something to target.

- **`repository/LevelTrackerRepository.java`** — removed the broken `upsertXp` (it referenced a constraint that was never created, and its `int` parameter silently truncated the fractional XP the RandomGenerator bonus produces). Replaced with two methods used together:
  - `insertIfAbsent(userId, activityId)` — a native `INSERT ... ON CONFLICT (user_id, activity_id) DO NOTHING`, returning the affected-row count. Handles the *first-write* race atomically (a row lock can't protect a row that doesn't exist yet).
  - `findByUserIdAndActivityIdForUpdate(userId, activityId)` — `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`), locking the row for the rest of the transaction. Handles the *concurrent-update* race.

- **`service/impl/LevelTrackerServiceImpl.java`** — rewrote `save()`: `insertIfAbsent` → lock the row → mutate `totalXp` → recompute level → persist, all in one transaction. No retry loop needed. The existing sealed `LevelOutcome` + `instanceof` pattern-matching logic is unchanged, just extracted into `applyLevel(...)`. (This file/package split happened during the rebase onto `main` — see below.)

- **`dao/LevelTrackerArchive.java`** — replaced the `@EmbeddedId (userId, activityId)` primary key with a generated surrogate `id`. The composite key allowed only one row per user+activity, so every save *overwrote* the previous archive entry via `merge()` — there was no history at all. `userId`/`activityId` are now plain columns.

- **`misc/LevelTrackerArchiveId.java`** — deleted (no longer needed).

- **`repository/LevelTrackerArchiveRepository.java`** — retyped to `JpaRepository<LevelTrackerArchive, Long>`; added `findByUserIdAndActivityIdOrderByArchivedAtDesc`.

- **Archive semantics:** one row is appended per update, containing a snapshot of the **previous** state (`level`, `totalXp`, `currentLevelXp`), taken *before* any mutation. No row is archived on the very first insert for a user+activity pair (there's no prior state to record). The earlier attempt built the snapshot *after* mutating `totalXp` but *before* recomputing the level, producing archive rows that mixed new XP with stale level data.

**Migration note:** this required recreating the dev database volume (`docker compose down -v`) — `ddl-auto: update` doesn't reliably add unique constraints, would fail outright if duplicate `(user_id, activity_id)` rows already existed from the live race, and doesn't migrate a primary key change on the archive table.

**Verified:** 20 truly concurrent `POST /level` requests (same user+activity, `+10` XP each) — see PR/commit for full evidence:
- `totalXp` came back **exactly** `200.0` (no lost updates)
- **exactly 1** `level_tracker` row (no duplicate-insert race)
- **exactly 19** `level_tracker_archive` rows, forming a coherent previous-state history (`10, 20, 30, ... 190`, no gaps, no duplicates)
- Zero deadlocks/lock timeouts/exceptions in logs during the concurrent burst
- Regression-checked: negative-XP validation (`400 ProblemDetail`) still works and doesn't leak a lock (it's rejected at JSON deserialization, before the transaction starts)

---

## Rebase onto `main`

Rebased this branch onto `main`, which had picked up a service-layer refactor (interfaces + `impl/` packages) plus new test suites and optimized Dockerfiles from other contributors in the meantime. One real conflict, in `LevelTrackerService.java` — `main` had gutted it into a pure interface and moved the (still-buggy, pre-fix) logic into a new `service/impl/LevelTrackerServiceImpl.java`. Resolution:

- Kept `main`'s interface split as-is (`LevelTrackerService` interface unchanged; no new public methods were needed).
- Moved this fix's `save()` implementation — `insertIfAbsent` → lock → mutate → `applyLevel` → `archivePreviousState` — into the new `LevelTrackerServiceImpl`, adding `LevelTrackerArchiveRepository` as a third constructor dependency.
- `LevelTrackerRepository`: kept `main`'s plain `findByUserIdAndActivityId` (still exercised directly by `main`'s new `LevelTrackerRepositoryTest`) alongside this fix's `findByUserIdAndActivityIdForUpdate` and `insertIfAbsent` — the concurrency-safe path is only used by `save()`.
- `main`'s new `LevelTrackerServiceImplTest` mocked the *old* buggy flow directly (`findByUserIdAndActivityId` mocked in-place) in 4 `save()`-related tests. Rewrote those 4 to mock the new flow (`insertIfAbsent` + `findByUserIdAndActivityIdForUpdate`) instead of reverting the fix to fit the old tests. Also added assertions that `LevelTrackerArchiveRepository.save()` is called with the correct previous-state snapshot on updates, and never called on first-time creates.

**Verified post-rebase:**
- Full `gamification-service` suite: 37/37 tests pass (`mvn clean test`).
- `activity-service` and `eureka-server` suites also pass unaffected (36/37 combined across activity-service tests + eureka-server).
- Re-ran the 20-concurrent-request race test against the rebased/rebuilt stack: identical result (`totalXp: 200.0`, 1 row, 19 coherent archive rows, zero errors in logs) — the fix survived the architectural merge intact.
- Note: `api-gateway`'s `ApiGatewayApplicationTests` fails under a bare `mvn test` (`UnknownHostException: postgres` — that test's `@SpringBootTest` needs the `postgres` hostname, which only resolves inside the docker-compose network). Confirmed via `git diff main -- api-gateway/` (zero diff) that this is **pre-existing on `main`**, not introduced by this rebase.
