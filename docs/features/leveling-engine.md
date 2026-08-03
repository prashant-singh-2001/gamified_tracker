# Leveling Engine — XP Math, Sealed Outcomes, Threshold Curve

**Services:** `activity-service` (XP math) + `gamification-service` (level resolution) ·
**Key classes:** `Activity.effectiveXpMultiplier`, `Category.baseXpMultiplier`,
`LevelOutcome` (sealed interface), `LevelCurve`, `LevelProgress`, `ActivityLevelThresholdRepository`

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

### The default curve — `LevelCurve`

Explicit thresholds are per-activity seed data, and an activity nobody seeded used to sit at level 1
forever no matter how much XP it accumulated. `LevelCurve` supplies a formula-derived fallback:

```java
/** Cumulative XP required to reach {@code level}. Level 1 (or below) always requires 0. */
public double xpRequiredFor(int level) {
    if (level <= 1) return 0.0;
    int cappedLevel = Math.min(level, maxLevel);
    return baseXp * Math.pow(cappedLevel - 1, exponent);
}
```
With the defaults (`baseXp=100`, `exponent=1.5`), level 2 costs 100 XP, level 3 ≈ 283, level 10
≈ 2700 — superlinear, so each level takes longer than the last. It's a plain object with no Spring
or DB dependency, built once as a bean by `LevelingConfig`, so a level can be computed without a
threshold row existing anywhere.

`levelFor` inverts the formula rather than looping from level 1, then corrects:
```java
int level = 1 + (int) Math.floor(Math.pow(totalXp / baseXp, 1.0 / exponent));
level = Math.max(1, Math.min(level, maxLevel));

// Math.pow/floor can land a hair off an exact boundary — nudge onto the correct side.
while (level < maxLevel && xpRequiredFor(level + 1) <= totalXp) level++;
while (level > 1 && xpRequiredFor(level) > totalXp) level--;
```
The two correction loops are the detail worth noticing: `Math.pow` round-trips through a
fractional exponent, so a total sitting exactly on a boundary can floor to the wrong side. The loops
run at most one step in practice but make the result exactly consistent with `xpRequiredFor` — the
function that defines the curve — instead of *approximately* consistent.

**Precedence: explicit data wins, and never blends.**
```java
// No explicit threshold reached. Only fall back to the default curve when this activity has
// NO threshold rows at all — an activity with even one row must stay on its own data forever,
// never blend in formula-derived levels.
boolean useDefaultCurve = levelCurve.isEnabled()
        && activityLevelThresholdRepository.countForActivity(activityId) == 0;
```
A half-seeded activity (say, thresholds for levels 2 and 3 only) stays on its own data and simply
stops at level 3 — it does not silently continue up the formula. Mixing the two would make an
activity's curve change shape mid-progression as rows are added. Note `countForActivity` is queried
**only** on the empty-result path, so the common explicit-threshold case costs exactly the same
number of queries as before.

`LevelCurve.isEnabled()` is a config kill switch: setting `leveling.default-curve.enabled=false`
restores the old "unseeded activity stays level 1 forever" behaviour with no code rollback.

**Making the fallback visible** — `GET /threshold/activity/{activityId}?upToLevel=10` returns an
activity's *effective* ladder: its explicit rows if it has any, otherwise the curve materialized on
the fly for levels 1..`upToLevel`. Nothing is persisted either way, so the endpoint answers "what
does this activity's ladder actually look like right now?" without a client having to know which of
the two sources is in play — and without seeding rows just to find out.

### XP-to-next-level progress — `LevelProgress`

`LevelTrackerDto` now carries `xpForNextLevel` and `progressPercent` alongside the raw totals, so a
client can render a progress bar without re-deriving the band boundaries itself:

```java
public static LevelProgress toward(double totalXp, double currentLevelXp, double nextLevelXpRequired) {
    double bandStart = totalXp - currentLevelXp;      // cumulative XP where the current level began
    double span = nextLevelXpRequired - bandStart;    // size of the current level's XP band
    if (span <= 0) {
        return MAX_LEVEL;
    }
    double remaining = Math.max(nextLevelXpRequired - totalXp, 0.0);
    double percent = Math.min(Math.max(currentLevelXp / span * 100.0, 0.0), 100.0);
    return new LevelProgress(round2(remaining), round2(percent));
}
```
The percentage is computed against the **current level's band**, not against total XP — reaching
level 5 resets the bar to 0%, it doesn't show 80%. Both outputs are clamped and rounded to two
decimals, and `span <= 0` (nothing further to reach) returns the `MAX_LEVEL` constant —
`(0.0, 100.0)` — rather than dividing by zero.

**Honest gap:** progress is resolved from explicit threshold rows only (`findNextLevels`). An
activity running on the default curve has no next-threshold row, so it reports `MAX_LEVEL` —
`xpForNextLevel: 0, progressPercent: 100` — even though its *level* advances correctly via the
curve. The level and the progress bar therefore disagree for unseeded activities; making
`progressFor` curve-aware (mirroring the same "explicit rows win, no blending" precedence
`resolveLevel` uses above) is the outstanding piece.

## Config

`gamification-service/src/main/resources/application.yaml`:
```yaml
leveling:
  default-curve:
    # Kill switch: false restores the old "unseeded activity stays level 1 forever" behavior
    # without a rollback. xpRequiredFor(level) = base-xp * (level - 1) ^ exponent.
    enabled: true
    base-xp: 100.0
    exponent: 1.5
    max-level: 100
```
Explicit per-activity thresholds are row data (`activity_level_threshold`, seeded via
`POST /threshold`) and always take precedence over the above. XP multipliers are likewise
per-`Activity` row data, not application config.

## Try it

```bash
# Create an activity with NO xpMultiplier — falls back to STUDY's 1.5 base, not 0
curl -X POST http://localhost:8080/api/activity -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Reading","category":"STUDY","active":true}'

# Log against it WITHOUT seeding any threshold — the default curve still levels it up
# (100 XP -> level 2, ~283 XP -> level 3)
curl http://localhost:8080/api/level/user/1 -H "Authorization: Bearer $TOKEN"

# See the effective ladder — generated from the curve, since nothing is seeded yet
curl "http://localhost:8080/api/threshold/activity/1?upToLevel=5" -H "Authorization: Bearer $TOKEN"

# Seed a level-2 threshold to override the curve for this activity from here on
curl -X POST http://localhost:8080/api/threshold -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"activityId":1,"level":2,"xpRequired":100}'
# -> that one row switches activity 1 off the default curve permanently
```

## Related
[Concurrency-Safe XP Accumulation](concurrency-safe-xp.md) (where `applyLevel` is called from) ·
[Level-Up Notifications](level-up-notifications.md) (fires when `leveledUp` is true) ·
issue #10 (the multiplier fix)
