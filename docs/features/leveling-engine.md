# Leveling Engine — XP Math, Sealed Outcomes, Threshold Curve

**Services:** `activity-service` (XP math) + `gamification-service` (level resolution) ·
**Key classes:** `Activity.effectiveXpMultiplier`, `Category.baseXpMultiplier`,
`LevelOutcome` (sealed interface), `ActivityLevelThresholdRepository`

## What it is / why it's notable

Two small, well-modeled pieces of domain logic: how XP is calculated, and how a total-XP number
becomes a "level." Neither is complicated on its own — the interesting part is the *shape* of each
solution. XP resolution uses a two-tier override/default model with a sentinel that closed a real
latent bug. Level resolution is modeled as a Java 17 **sealed interface** with exhaustive pattern
matching instead of a boolean flag or a nullable field — the kind of type-safe domain modeling that
reads as "this developer knows the language," not just "this code works."

## How XP is computed

### Effective multiplier — override with category fallback

```java
// Activity.java
public double effectiveXpMultiplier() {
    if (xpMultiplier > 0) {
        return xpMultiplier;
    }
    return category != null ? category.baseXpMultiplier() : Category.OTHER.baseXpMultiplier();
}
```
```java
// Category.java
public double baseXpMultiplier() {
    return switch (this) {
        case STUDY, WORK -> 1.5;
        case HEALTH -> 1.3;
        case OTHER -> 1.0;
        case CHORES -> 0.8;
        case GAMING -> 0.5;
    };
}
```
The model: `xpMultiplier` is a **per-activity override** when positive; a non-positive stored value
(including the `0.0` a client gets by simply omitting the field) means "no override, use the
category's default." This sentinel design closed a real bug: because `xpMultiplier` is a primitive
`double` with no default in the create request, an activity created without one used to earn **zero
XP forever**. Now it silently — and correctly — falls back to its category's base. A negative
multiplier degrades gracefully the same way, instead of producing negative XP (which would otherwise
be rejected downstream by `LevelTrackerRequestDTO`'s `xp >= 0` guard). The method is named
`effectiveXpMultiplier()` rather than `getEffectiveXpMultiplier()` on purpose, so Jackson doesn't
serialize it into the `Activity` JSON embedded inside `ActivityLogResponse`.

### The bonus roll

```java
var random = ThreadLocalRandom.current();
double multiplier = activityLog.getActivity().effectiveXpMultiplier();
double bonus = random.nextDouble() < 0.2 ? random.nextDouble(1.1, 1.5) : 1.0;
activityLog.setXpEarned(activityLog.getDurationMinutes() * multiplier * bonus);
```
A ~20% chance of a `[1.1, 1.5)` bonus multiplier, surfaced to the client as `bonusApplied` +
`bonusMultiplier` on the response. Uses `ThreadLocalRandom` deliberately — a prior version used
`RandomGenerator.getDefault()`, which threw on some JVM/container images because they lacked a
registered `"L32X64MixRandom"` algorithm provider; `ThreadLocalRandom` has no such dependency.

## How a level is computed

### Sealed interface + pattern matching — `LevelOutcome`

```java
public sealed interface LevelOutcome permits LevelOutcome.LeveledUp, LevelOutcome.InProgress {
    record LeveledUp(int level, double currentLevelXp) implements LevelOutcome {}
    record InProgress(int level, double currentLevelXp) implements LevelOutcome {}
}
```
The whole file. Instead of a `boolean leveledUp` field threaded through the method, the level
decision is an algebraic type with exactly two possibilities, each carrying only the data relevant
to that case. Consumed with exhaustive `instanceof` pattern matching in
`LevelTrackerServiceImpl.applyLevel`:
```java
LevelOutcome outcome = reachedLevels.isEmpty()
        ? new LevelOutcome.InProgress(1, levelTracker.getTotalXp())
        : new LevelOutcome.LeveledUp(
                reachedLevels.get(0).getId().getLevel(),
                levelTracker.getTotalXp() - reachedLevels.get(0).getXpRequired());

boolean leveledUp = false;
if (outcome instanceof LevelOutcome.LeveledUp up) {
    levelTracker.setLevel(up.level());
    levelTracker.setCurrentLevelXp(up.currentLevelXp());
    leveledUp = true;
} else if (outcome instanceof LevelOutcome.InProgress ip) {
    levelTracker.setLevel(ip.level());
    levelTracker.setCurrentLevelXp(ip.currentLevelXp());
    leveledUp = false;
}
```

### The threshold curve — composite key + ordered/paged query

```java
// ActivityLevelThresholdId — @EmbeddedId composite key
public class ActivityLevelThresholdId implements Serializable {
    private Long activityId;
    private Integer level;
}
```
```java
@Query("""
        SELECT a FROM ActivityLevelThreshold a
        WHERE a.id.activityId = :activityId AND a.xpRequired <= :xp
        ORDER BY a.id.level DESC
        """)
List<ActivityLevelThreshold> findReachedLevels(@Param("activityId") Long activityId,
                                                @Param("xp") double xp, Pageable pageable);
```
Called with `PageRequest.of(0, 1)` — `ORDER BY level DESC` plus a limit-1 page turns "every threshold
this XP total has crossed" into "the single highest one," in one query, with no in-memory sorting.
`currentLevelXp` is then `totalXp − thatThreshold.xpRequired`.

## Config

The threshold curve has no default seed — every activity starts at level 1 until a threshold is
`POST`ed for it (`activity_level_threshold`, `POST /threshold`). No config keys; XP multipliers are
per-`Activity` row data, not application config.

## Try it

```bash
# Create an activity with NO xpMultiplier — falls back to STUDY's 1.5 base, not 0
curl -X POST http://localhost:8080/api/activity -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Reading","category":"STUDY","active":true}'

# Seed a level-2 threshold, then log enough time to cross it
curl -X POST http://localhost:8080/api/threshold -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"activityId":1,"level":2,"xpRequired":100}'
```

## Related
[Concurrency-Safe XP Accumulation](concurrency-safe-xp.md) (where `applyLevel` is called from) ·
[Level-Up Notifications](level-up-notifications.md) (fires when `leveledUp` is true) ·
issue #10 (the multiplier fix)
