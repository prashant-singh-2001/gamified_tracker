# Concurrency-Safe XP Accumulation

**Service:** `gamification-service` · **Key classes:** `LevelTrackerServiceImpl.save`,
`LevelTrackerRepository`, `LevelTrackerArchive`

## What it is / why it's notable

A classic read-modify-write race, closed with three cooperating mechanisms instead of one. If two
`POST /level` requests for the same `(userId, activityId)` land at nearly the same instant — plausible
here, since the [event-driven consumer](event-driven-decoupling.md) and a direct HTTP call can both
be applying XP concurrently — a naive "read totalXp, add xp, write totalXp" loses one of the two
updates. This code prevents that with an atomic upsert, a pessimistic row lock, and a database unique
constraint as the final backstop — three independent layers, each catching what the one before it
might miss. It also snapshots every previous state to an append-only archive before mutating, so the
fix produces an audit trail as a side effect.

## How it works

```mermaid
sequenceDiagram
    participant T1 as Writer 1
    participant T2 as Writer 2
    participant DB as level_tracker row

    par concurrent requests
        T1->>DB: insertIfAbsent(user, activity) — ON CONFLICT DO NOTHING
        T2->>DB: insertIfAbsent(user, activity) — ON CONFLICT DO NOTHING
    end
    Note over DB: exactly one INSERT wins; both see created=false or true consistently
    T1->>DB: SELECT ... FOR UPDATE (locks the row)
    Note over T2: T2's SELECT ... FOR UPDATE blocks here
    T1->>DB: archive previous state, totalXp += xp, save, COMMIT (releases lock)
    T2->>DB: SELECT ... FOR UPDATE proceeds now, sees T1's committed totalXp
    T2->>DB: archive previous state, totalXp += xp, save, COMMIT
    Note over DB: both increments applied — no lost update
```

### 1. Atomic upsert — guarantee the row exists, race-free

```java
@Modifying(flushAutomatically = true)
@Query(value = """
        INSERT INTO level_tracker (user_id, activity_id, total_xp, current_level_xp)
        VALUES (:userId, :activityId, 0, 0)
        ON CONFLICT (user_id, activity_id) DO NOTHING
        """, nativeQuery = true)
int insertIfAbsent(@Param("userId") Long userId, @Param("activityId") Long activityId);
```
A native Postgres `ON CONFLICT DO NOTHING` — the database itself resolves the "does this row exist
yet" race, so two concurrent first-time writers never both try to `INSERT` and one fail. The return
value (`1` if it inserted, `0` if the row already existed) tells the caller whether to archive.

### 2. Pessimistic row lock — serialize the update

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT l FROM LevelTracker l WHERE l.userId = :userId AND l.activityId = :activityId")
Optional<LevelTracker> findByUserIdAndActivityIdForUpdate(@Param("userId") Long userId,
                                                            @Param("activityId") Long activityId);
```
Translates to `SELECT ... FOR UPDATE`. The second concurrent transaction blocks on this query until
the first one commits — so by the time it reads `totalXp`, it's reading the *post-update* value, not
a stale one. This is what actually prevents the lost update; the upsert above only handles row
creation.

### 3. The full sequence — `LevelTrackerServiceImpl.save`

```java
@Transactional
public LevelTrackerDto save(Long userId, LevelTrackerRequestDTO dto) {
    boolean created = levelTrackerRepository.insertIfAbsent(userId, dto.activityId()) == 1;

    var tracker = levelTrackerRepository
            .findByUserIdAndActivityIdForUpdate(userId, dto.activityId())
            .orElseThrow(); // guaranteed to exist; row is now locked for the rest of this transaction

    if (!created) {
        archivePreviousState(tracker);   // snapshot BEFORE mutation
    }

    Integer oldLevel = tracker.getLevel();
    tracker.setTotalXp(tracker.getTotalXp() + dto.xp());
    boolean leveledUp = applyLevel(tracker);   // see Leveling Engine

    var saved = levelTrackerRepository.save(tracker);
    // ... conditionally emit a LevelUpEvent — see Level-Up Notifications ...
    return mapToDto(saved, leveledUp);
}
```
One `@Transactional` method, no retry loop needed — the lock makes retries unnecessary, since a
concurrent writer simply waits rather than racing.

### 4. The backstop — a DB unique constraint

```java
@Table(name = "LevelTracker",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_level_tracker_user_activity",
                columnNames = {"user_id", "activity_id"}))
```
Even if application logic had a bug, the database physically cannot hold two rows for the same
`(user_id, activity_id)` — this is the constraint the `ON CONFLICT` target above relies on.

### 5. Append-only audit trail — `archivePreviousState`

```java
private void archivePreviousState(LevelTracker tracker) {
    levelTrackerArchiveRepository.save(
            LevelTrackerArchive.builder()
                    .userId(tracker.getUserId()).activityId(tracker.getActivityId())
                    .level(tracker.getLevel()).totalXp(tracker.getTotalXp())
                    .currentLevelXp(tracker.getCurrentLevelXp())
                    .archivedAt(LocalDateTime.now())
                    .build());
}
```
Called only when `!created` — a brand-new row has no prior state worth archiving. Each snapshot is a
new row in a separate table (its own surrogate PK), so history accumulates rather than being
overwritten; `LevelTrackerArchiveRepository.findByUserIdAndActivityIdOrderByArchivedAtDesc` reads it
back newest-first.

## Config

None external — this is pure JPA/Postgres locking semantics. Requires the shared `tracker_db`
Postgres instance (`docker-compose.yml`).

## Try it

Fire a burst of concurrent `POST /api/level` (or `/api/activitylog`, which drives it via the
[event-driven path](event-driven-decoupling.md)) for the same activity and confirm the resulting
`totalXp` equals the exact sum of every request's `xp` — not less. A 20-request concurrent burst is
the standard way this was verified during development.

## Related
[Leveling Engine](leveling-engine.md) (what happens inside `applyLevel`) ·
[Event-Driven Decoupling](event-driven-decoupling.md) (the other caller of `save`) ·
[Level-Up Notifications](level-up-notifications.md) (emitted from this same transaction)
