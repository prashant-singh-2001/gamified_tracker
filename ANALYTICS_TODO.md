# TODO — Analytics Endpoints: XP over time, per-category, weekly report (issue #17)

> Guide to the analytics endpoints: **What to do**, **Why to do it**, and **exact code**.
> Target branch: `17-add-analytics-endpoints` (currently identical to `main` — 0 ahead, 0 behind).
> **Status: not yet implemented.** This is a plan, not a record of work done.

## The problem in one picture

Issue #17 is three words: *"XP over time, per-category summaries, weekly reports."* The real analysis is
**where the data lives** — and it is not where the feature sounds like it belongs.

`gamification-service` owns the *concept* of XP (leaderboards, ranks, levels) but **cannot answer a single
one of these three questions**:

```
level_tracker         -> running total, mutated in place, ZERO timestamp columns
grep -ri category     -> ZERO matches in the entire gamification-service tree
ActivityLoggedEvent   -> (logId, userId, activityId, xpEarned)   no category, no timestamp
```

| Question | gamification-service | activity-service |
|---|---|---|
| XP over time | **No** — totals only, no time dimension | **Yes** — `activity_log.xp_earned` is a per-event delta with `created_at` |
| Per-category | **No** — knows only a bare `activityId` | **Yes** — `ActivityLog → @ManyToOne Activity → @Enumerated Category` |
| Weekly report | **No** | **Yes** |

`level_tracker_archive.archived_at` looks like an escape hatch but is lossy: it skips the **first** award
per (user, activity), never holds the **current** total, stores no per-award delta, and `archived_at` is
unindexed. Building on it would require either a **breaking change to `ActivityLoggedEvent`** — guarded by
`ActivityLoggedEventWireFormatTest`, and it would invalidate every unpublished `outbox_event.payload` row —
plus a new XP-ledger table, or a **new synchronous Feign call** back to activity-service, reintroducing
exactly the coupling that #16 removed.

**activity-service already has the complete fact table.** So this is a purely read-only feature: new
queries, no new writes, no fact schema, no cross-service traffic.

### The time-axis trap (read this before writing any query)

`ActivityLogRequest` validates:

```java
@NotNull @FutureOrPresent LocalDateTime startTime,
@NotNull @Future          LocalDateTime endTime,
```

The API therefore only accepts logs for activities in the **future**, so `start_time` sits systematically
*ahead* of `created_at` (which the server sets to `now()` in `ActivityLogServiceImpl.mapToActivityLog`).
Bucketing by `start_time` would plot every chart forward in time.

⇒ **The analytics axis is `created_at`**: server-set, monotonic, never client-spoofable. Switching later is
a one-line change. See *Out of scope* for the separate issue this deserves.

## Locked decisions

| Decision | Choice | Why |
|---|---|---|
| Service | **activity-service** | The only service holding per-event XP + timestamps + category. Zero new coupling |
| Aggregation | **On-read SQL `GROUP BY`** | No rollup table, no scheduler, never stale. Data volume doesn't justify materializing |
| Auth | **Self-only**, `@RequestHeader("userId")` | No `{userId}` path param ⇒ no IDOR surface at all. Matches `/ranks/me`, `/leaderboard/me` |
| Time axis | **`created_at`** | `start_time` is forced into the future by validation — see above |
| Time buckets | **Zero-filled** | Idle days return an explicit `0` row, so clients get a continuous series to chart |
| Bucketing | **DAY in SQL, WEEK/MONTH rolled up in Java** | `date_trunc` is Postgres-only and would break the H2 tests; dialects also disagree on the first day of the week |

## Repo conventions to honor
- **Comment out replaced lines, don't delete them.**
- **Do not commit** — the maintainer commits.
- This doc stays **untracked**; repo-root `*_TODO.md` files are not part of the PR.
- activity-service services return `ResponseEntity<...>` **directly** from the service layer (unusual, but
  consistent across the whole module) — follow it.
- All DTOs in this module are **records**, no Lombok. Projections follow gamification-service's
  `UserXpProjection` idiom: a plain interface, `getXxx()` with **boxed** types.

---

## Step 1 — Projections and DTOs

**What:** Two interface projections and four response types under `activity-service/.../dto/`.

**Why:** activity-service has **zero** projections today — this establishes the pattern locally, copied
from gamification-service. Boxed types are load-bearing: `SUM(...)` returns `NULL` for an empty group, and
a primitive getter would throw on unboxing.

### `dto/DailyXpProjection.java` (new)

```java
package com.tracker.activity.dto;

/**
 * One day's XP roll-up. The JPQL `AS` aliases in
 * {@code ActivityLogRepository.sumXpByDay} must match these getter names exactly
 * (de-capitalized), or Spring Data cannot bind the row.
 *
 * Deliberately NOT named year/month/day: those collide with the HQL functions of the
 * same name used in the GROUP BY.
 */
public interface DailyXpProjection {
    Integer getBucketYear();

    Integer getBucketMonth();

    Integer getBucketDay();

    Double getTotalXp();

    Long getLogCount();
}
```

### `dto/CategorySummaryProjection.java` (new)

```java
package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

public interface CategorySummaryProjection {
    Category getCategory();

    Double getTotalXp();

    Long getLogCount();

    Long getTotalMinutes();
}
```

### `dto/Bucket.java` (new)

```java
package com.tracker.activity.dto;

/**
 * Time-bucket granularity for the XP series. Binds directly as a @RequestParam, the way
 * RankController binds RankTier as a @PathVariable.
 */
public enum Bucket {
    DAY,
    WEEK,
    MONTH
}
```

### `dto/XpBucketDto.java` (new)

```java
package com.tracker.activity.dto;

import java.time.LocalDate;

/** One point on the XP series. {@code bucketStart} is inclusive: the day, ISO-Monday, or 1st of month. */
public record XpBucketDto(LocalDate bucketStart, double totalXp, long logCount) {
}
```

### `dto/CategorySummaryDto.java` (new)

```java
package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

public record CategorySummaryDto(Category category, double totalXp, long logCount, long totalMinutes) {
}
```

### `dto/WeeklyReportDto.java` (new)

```java
package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

import java.time.LocalDate;
import java.util.List;

/**
 * One week of activity in a single call. {@code weekStart} is the ISO-8601 Monday and
 * {@code weekEnd} the following Sunday, both inclusive. {@code topCategory} is null when the
 * week is empty.
 */
public record WeeklyReportDto(
        LocalDate weekStart,
        LocalDate weekEnd,
        double totalXp,
        long totalLogs,
        long totalMinutes,
        Category topCategory,
        double previousWeekXp,
        double xpChangeVsPreviousWeek,
        List<XpBucketDto> daily,
        List<CategorySummaryDto> byCategory
) {
}
```

---

## Step 2 — Two aggregation queries on `ActivityLogRepository`

**What:** Add `sumXpByDay` and `summarizeByCategory` to
`activity-service/src/main/java/com/tracker/activity/repository/ActivityLogRepository.java`.

**Why:** The interface currently holds exactly `findByUserId` and nothing else — the entire service has no
`@Query`, no `Pageable`, no projection. These two queries are the whole data layer for the feature.

**The portability constraint that shapes them:** production is PostgreSQL, but **tests run on H2 with
`ddl-auto=create-drop` and Flyway not applied**. `date_trunc('week', …)` is Postgres-only. So group at
**day** granularity using HQL's `YEAR()/MONTH()/DAY()` (Hibernate 6 renders these as
`extract(… from …)`, supported by both dialects) and roll up to week/month in Java.

`COALESCE` is not decoration: `xp_earned` and `duration_minutes` are **nullable in the DDL** even though
`xpEarned` is a primitive `double` in the entity.

```java
package com.tracker.activity.repository;

import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dto.CategorySummaryProjection;
import com.tracker.activity.dto.DailyXpProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByUserId(Long userId);

    // Analytics (#17): daily XP series. Grouped with HQL YEAR()/MONTH()/DAY(), which Hibernate 6
    // renders as extract(... from ...) — supported by BOTH PostgreSQL and H2, unlike date_trunc.
    // Week/month roll-up happens in AnalyticsServiceImpl, so week boundaries are explicit
    // ISO-8601 Mondays rather than whatever the dialect decides.
    // The axis is createdAt (server-set), NOT startTime — see ActivityLogRequest's @Future validation.
    @Query("""
            SELECT YEAR(l.createdAt) AS bucketYear,
                   MONTH(l.createdAt) AS bucketMonth,
                   DAY(l.createdAt) AS bucketDay,
                   COALESCE(SUM(l.xpEarned), 0.0) AS totalXp,
                   COUNT(l) AS logCount
            FROM ActivityLog l
            WHERE l.userId = :userId
              AND l.createdAt >= :from
              AND l.createdAt < :to
            GROUP BY YEAR(l.createdAt), MONTH(l.createdAt), DAY(l.createdAt)
            ORDER BY YEAR(l.createdAt), MONTH(l.createdAt), DAY(l.createdAt)
            """)
    List<DailyXpProjection> sumXpByDay(@Param("userId") Long userId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    // Analytics (#17): per-category roll-up. Category lives on the joined Activity, not on
    // activity_log, so this needs the join. Ordered by XP desc so the head row is the top category.
    @Query("""
            SELECT a.category AS category,
                   COALESCE(SUM(l.xpEarned), 0.0) AS totalXp,
                   COUNT(l) AS logCount,
                   COALESCE(SUM(l.durationMinutes), 0) AS totalMinutes
            FROM ActivityLog l
            JOIN l.activity a
            WHERE l.userId = :userId
              AND l.createdAt >= :from
              AND l.createdAt < :to
            GROUP BY a.category
            ORDER BY COALESCE(SUM(l.xpEarned), 0.0) DESC
            """)
    List<CategorySummaryProjection> summarizeByCategory(@Param("userId") Long userId,
                                                        @Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);
}
```

> **⚠ Unverified assumption — test this first.** `YEAR()/MONTH()/DAY()` as HQL functions is reasoned from
> the Hibernate 6 function set, **not** confirmed by running it here, and nothing in this repo uses them
> today. **Write `ActivityLogRepositoryTest` (Step 6) before anything else** and run it. If it fails, the
> drop-in fallback is the JPA 3.1 standard form — same projection, same Java roll-up, no other change:
>
> ```java
> SELECT EXTRACT(YEAR FROM l.createdAt) AS bucketYear,
>        EXTRACT(MONTH FROM l.createdAt) AS bucketMonth,
>        EXTRACT(DAY FROM l.createdAt) AS bucketDay,
> ...
> GROUP BY EXTRACT(YEAR FROM l.createdAt), EXTRACT(MONTH FROM l.createdAt), EXTRACT(DAY FROM l.createdAt)
> ```

---

## Step 3 — `AnalyticsService` interface

**What:** New `activity-service/.../service/AnalyticsService.java`.
**Why:** Matches the module's interface + `impl/` split (`ActivityService`/`ActivityServiceImpl`).

```java
package com.tracker.activity.service;

import com.tracker.activity.dto.Bucket;
import com.tracker.activity.dto.CategorySummaryDto;
import com.tracker.activity.dto.WeeklyReportDto;
import com.tracker.activity.dto.XpBucketDto;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    ResponseEntity<List<XpBucketDto>> getXpOverTime(Long userId, LocalDate from, LocalDate to, Bucket bucket);

    ResponseEntity<List<CategorySummaryDto>> getByCategory(Long userId, LocalDate from, LocalDate to);

    ResponseEntity<WeeklyReportDto> getWeeklyReport(Long userId, LocalDate weekStart);
}
```

---

## Step 4 — `AnalyticsServiceImpl` — the only real logic

**What:** New `activity-service/.../service/impl/AnalyticsServiceImpl.java`.

**Why:** Three things have to happen outside SQL:
1. **Range normalisation** — params arrive as `LocalDate`; the query needs `LocalDateTime`. `to` is made
   inclusive-by-day (`to.plusDays(1).atStartOfDay()`, exclusive upper bound), which is what a caller
   expects from `?to=2026-07-28`.
2. **Zero-fill + roll-up** — a `GROUP BY` emits no row for an idle day. Pre-fill the range, then overwrite
   the days that have data. Rolling week/month up here is what buys dialect-independent ISO-Monday weeks.
3. **Validation** — reuse the **existing** `InvalidTimeRangeException`, already mapped to a `400
   ProblemDetail` by `GlobalExceptionHandler`. Deliberately **not** `@Validated` + `@RequestParam`
   constraints: `ActivityLogController` has no class-level `@Validated`, and there is **no
   `ConstraintViolationException` handler**, so those would either silently not fire or produce a raw 500.

```java
package com.tracker.activity.service.impl;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.Bucket;
import com.tracker.activity.dto.CategorySummaryDto;
import com.tracker.activity.dto.DailyXpProjection;
import com.tracker.activity.dto.WeeklyReportDto;
import com.tracker.activity.dto.XpBucketDto;
import com.tracker.activity.exception.InvalidTimeRangeException;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.AnalyticsService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    // Bounds the zero-fill loop. Without it, ?from=1970-01-01 would materialise ~20k
    // XpBucketDto instances in memory for no useful purpose.
    private static final long MAX_RANGE_DAYS = 366;
    private static final long DEFAULT_RANGE_DAYS = 30;

    private final ActivityLogRepository activityLogRepository;

    @Override
    public ResponseEntity<List<XpBucketDto>> getXpOverTime(Long userId, LocalDate from, LocalDate to,
                                                           Bucket bucket) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(DEFAULT_RANGE_DAYS);
        validateRange(fromDate, toDate);

        Map<LocalDate, XpBucketDto> daily = dailySeries(userId, fromDate, toDate);
        return ResponseEntity.ok(rollUp(daily, bucket != null ? bucket : Bucket.DAY));
    }

    @Override
    public ResponseEntity<List<CategorySummaryDto>> getByCategory(Long userId, LocalDate from, LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(DEFAULT_RANGE_DAYS);
        validateRange(fromDate, toDate);

        return ResponseEntity.ok(categorySummary(userId, fromDate, toDate));
    }

    @Override
    public ResponseEntity<WeeklyReportDto> getWeeklyReport(Long userId, LocalDate weekStart) {
        LocalDate anchor = weekStart != null ? weekStart : LocalDate.now();
        // Accept ANY day in the week and normalise to its ISO-8601 Monday, so callers don't
        // have to know which day the week starts on.
        LocalDate start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);

        // ONE query over 14 days (previous week + this week), split in Java, so the
        // week-over-week delta costs no extra round trip.
        Map<LocalDate, XpBucketDto> fortnight = dailySeries(userId, start.minusWeeks(1), end);

        List<XpBucketDto> daily = fortnight.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(start))
                .map(Map.Entry::getValue)
                .toList();

        double previousWeekXp = fortnight.entrySet().stream()
                .filter(e -> e.getKey().isBefore(start))
                .mapToDouble(e -> e.getValue().totalXp())
                .sum();

        double totalXp = daily.stream().mapToDouble(XpBucketDto::totalXp).sum();
        long totalLogs = daily.stream().mapToLong(XpBucketDto::logCount).sum();

        List<CategorySummaryDto> byCategory = categorySummary(userId, start, end);
        long totalMinutes = byCategory.stream().mapToLong(CategorySummaryDto::totalMinutes).sum();
        // summarizeByCategory already ORDER BYs XP desc, so the head row is the top category.
        Category topCategory = byCategory.isEmpty() ? null : byCategory.get(0).category();

        return ResponseEntity.ok(new WeeklyReportDto(
                start, end, totalXp, totalLogs, totalMinutes, topCategory,
                previousWeekXp, totalXp - previousWeekXp, daily, byCategory));
    }

    /**
     * Zero-filled day -> bucket map, in chronological order. Pre-fills every day in the range
     * so idle days still appear in the series, then overwrites the days that have data
     * (LinkedHashMap keeps the original insertion position on replace, so order survives).
     */
    private Map<LocalDate, XpBucketDto> dailySeries(Long userId, LocalDate from, LocalDate to) {
        // Upper bound is exclusive, so +1 day makes `to` inclusive for the caller.
        List<DailyXpProjection> rows = activityLogRepository.sumXpByDay(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<LocalDate, XpBucketDto> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new XpBucketDto(d, 0.0, 0L));
        }
        for (DailyXpProjection row : rows) {
            LocalDate day = LocalDate.of(row.getBucketYear(), row.getBucketMonth(), row.getBucketDay());
            byDay.put(day, new XpBucketDto(
                    day,
                    row.getTotalXp() != null ? row.getTotalXp() : 0.0,
                    row.getLogCount() != null ? row.getLogCount() : 0L));
        }
        return byDay;
    }

    private List<XpBucketDto> rollUp(Map<LocalDate, XpBucketDto> daily, Bucket bucket) {
        if (bucket == Bucket.DAY) {
            return List.copyOf(daily.values());
        }

        Map<LocalDate, Double> xp = new LinkedHashMap<>();
        Map<LocalDate, Long> logs = new LinkedHashMap<>();
        daily.forEach((day, dto) -> {
            LocalDate key = bucketStartFor(day, bucket);
            xp.merge(key, dto.totalXp(), Double::sum);
            logs.merge(key, dto.logCount(), Long::sum);
        });

        return xp.entrySet().stream()
                .map(e -> new XpBucketDto(e.getKey(), e.getValue(), logs.get(e.getKey())))
                .toList();
    }

    private LocalDate bucketStartFor(LocalDate day, Bucket bucket) {
        return switch (bucket) {
            // ISO-8601 week = Monday. Pinned here rather than left to the DB: date_trunc('week')
            // is Postgres-only, and dialects disagree on the first day of the week.
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
            case DAY -> day;
        };
    }

    private List<CategorySummaryDto> categorySummary(Long userId, LocalDate from, LocalDate to) {
        return activityLogRepository
                .summarizeByCategory(userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(r -> new CategorySummaryDto(
                        r.getCategory(),
                        r.getTotalXp() != null ? r.getTotalXp() : 0.0,
                        r.getLogCount() != null ? r.getLogCount() : 0L,
                        r.getTotalMinutes() != null ? r.getTotalMinutes() : 0L))
                .toList();
    }

    // Reuses InvalidTimeRangeException (already -> 400 ProblemDetail in GlobalExceptionHandler)
    // rather than @RequestParam constraints, which would need @Validated plus a
    // ConstraintViolationException handler that this service does not have.
    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidTimeRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new InvalidTimeRangeException("Range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }
}
```

---

## Step 5 — `AnalyticsController`

**What:** New `activity-service/.../controller/AnalyticsController.java`.

**Why:** No `/api` prefix — the gateway strips it. Identity comes from the trusted `userId` header the
gateway's `JwtFilter` injects (it overwrites/strips any client-sent value), so there is **no `{userId}`
path variable and therefore no IDOR surface**. `@DateTimeFormat` is explicit because the module configures
no global date converter.

```java
package com.tracker.activity.controller;

import com.tracker.activity.dto.Bucket;
import com.tracker.activity.dto.CategorySummaryDto;
import com.tracker.activity.dto.WeeklyReportDto;
import com.tracker.activity.dto.XpBucketDto;
import com.tracker.activity.service.AnalyticsService;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only analytics over the caller's own activity logs (issue #17).
 * Every endpoint is self-scoped: the user comes from the gateway-injected `userId` header,
 * never from the path or body.
 */
@AllArgsConstructor
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/xp-over-time")
    public ResponseEntity<List<XpBucketDto>> getXpOverTime(
            @RequestHeader("userId") Long userId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "bucket", defaultValue = "DAY") Bucket bucket) {
        return analyticsService.getXpOverTime(userId, from, to, bucket);
    }

    @GetMapping("/by-category")
    public ResponseEntity<List<CategorySummaryDto>> getByCategory(
            @RequestHeader("userId") Long userId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.getByCategory(userId, from, to);
    }

    @GetMapping("/weekly-report")
    public ResponseEntity<WeeklyReportDto> getWeeklyReport(
            @RequestHeader("userId") Long userId,
            @RequestParam(name = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return analyticsService.getWeeklyReport(userId, weekStart);
    }
}
```

---

## Step 6 — Gateway route (one line)

**What:** `api-gateway/src/main/java/com/tracker/gateway/config/RouteConfiguration.java` — add one
predicate to the **existing** `activityRoute` bean.

**Why:** Route predicates are an **enumerated `.or(path(...))` chain, not a catch-all**, so
`/api/analytics/**` would 404 without this. Reuse the activity route rather than adding a bean: a new bean
would need a new `Bucket` component on the `RateLimitProperties` record **and** a matching
`rate-limit.analytics.*` YAML block, or the record binds `null` and the bean NPEs at startup. Reusing
inherits the activity bucket — no config change anywhere.

The `rewritePath("^/api/(activity|activitylog)/?$", "/$1/")` regex needs **no** change: it only matches
bare base paths, and every analytics path has sub-segments, so the generic `^/api/(.*)$` strip handles them.

```java
        return route("activity").route(
                        path("/api/activity/**")
                                .or(path("/api/activitylog/**"))
                                // Analytics (#17) rides the activity route so it inherits that
                                // rate-limit bucket; a separate route bean would need its own
                                // RateLimitProperties.Bucket or it NPEs at startup.
                                .or(path("/api/analytics/**")),
                        http())
```

No change to `SecurityConfig` — `.anyRequest().authenticated()` already covers the new prefix. No change to
`JwtFilter`.

---

## Step 7 — `V4__create_analytics_indexes.sql`

**What:** New `activity-service/src/main/resources/db/migration/V4__create_analytics_indexes.sql`.

**Why:** The only index on `activity_log` today is `idx_activity_log_user_id` on `(user_id)`. Every
analytics query filters `user_id` **and** ranges on `created_at`, and the category summary joins on
`activity_id` — which Postgres does **not** auto-index for a foreign key, so that join currently seq-scans.
Hibernate runs `ddl-auto: validate`, so indexes must arrive via Flyway.

```sql
-- Analytics endpoints (issue #17): every query is
--   WHERE user_id = ? AND created_at >= ? AND created_at < ?
-- The leading user_id also still serves the existing findByUserId, but
-- idx_activity_log_user_id is left in place rather than dropped (repo convention:
-- additive migrations only).
CREATE INDEX idx_activity_log_user_id_created_at
    ON activity_log (user_id, created_at);

-- The per-category summary joins activity_log -> activity. PostgreSQL does not create an
-- index for a foreign key automatically, so this join is currently a sequential scan.
CREATE INDEX idx_activity_log_activity_id
    ON activity_log (activity_id);
```

---

## Step 8 — Tests

### 8a. `repository/ActivityLogRepositoryTest.java` (new) — **write this first**

**Why first:** it is the only thing that proves the HQL date functions actually translate. Everything else
is built on top. `@DataJpaTest` with `@Autowired` field injection + a static builder helper, matching
`ActivityStreakRepositoryTest` (constructor injection needs `@TestConstructor`, which isn't configured).

Cover:
- `sumXpByDay` groups by day and returns rows in chronological order
- `sumXpByDay` respects the range bounds (`>= from` inclusive, `< to` exclusive)
- `sumXpByDay` returns **no** row for an idle day (proving the service must zero-fill)
- another user's rows never leak into either query
- `summarizeByCategory` groups by the `@Enumerated(STRING)` enum across a join, ordered by XP desc
- `COALESCE` holds when `xp_earned` / `duration_minutes` are null

Note the fixture must set `createdAt` **explicitly** — it is the axis, and `@DataJpaTest` won't populate it.

### 8b. `service/AnalyticsServiceImplTest.java` (new)

`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`, no Spring context.

**The trap:** a mocked repository **cannot** return a Spring Data projection proxy, so each projection
needs a plain implementation declared at the bottom of the test class — the same idiom
`LeaderboardServiceImplTest` documents:

```java
    private record DailyRow(Integer bucketYear, Integer bucketMonth, Integer bucketDay,
                            Double totalXp, Long logCount) implements DailyXpProjection {
        @Override public Integer getBucketYear() { return bucketYear; }
        @Override public Integer getBucketMonth() { return bucketMonth; }
        @Override public Integer getBucketDay() { return bucketDay; }
        @Override public Double getTotalXp() { return totalXp; }
        @Override public Long getLogCount() { return logCount; }
    }
```

Cover: idle days zero-filled; DAY passthrough; WEEK roll-up lands on the ISO Monday; MONTH roll-up lands on
the 1st; the weekly report issues **one** `sumXpByDay` call spanning 14 days and splits it correctly;
`xpChangeVsPreviousWeek` arithmetic; `topCategory == null` on an empty week; `from > to` →
`InvalidTimeRangeException`; a >366-day range → `InvalidTimeRangeException`.

### 8c. `controller/AnalyticsControllerTest.java` (new)

`@WebMvcTest(AnalyticsController.class)` + `@MockitoBean` (**not** the deprecated `@MockBean`), `jsonPath`
assertions, trailing `verify(...)`. Include a test that the `userId` **header** is what identifies the
caller, and one that a missing `userId` header is rejected.

---

## Step 9 — Docs

- **`API.md`** — new `### Analytics` group under `## API Gateway (port 8080) — public surface`, using the
  Method/Path/Auth/Description table idiom (the newer of the two styles in that file), plus entries in the
  internal `## Activity Service (port 8081)` section.
- **`activity-service/README.md`** — a `### Analytics — /analytics` subsection under `## API reference`,
  alongside the existing `### Activities — /activity` and `### Activity Logs — /activitylog`.
- **`api-gateway/README.md`** — the "Gateway routes" table predicate cell (line ~152). It is **already
  stale** — missing `/api/leaderboard/**` and `/api/ranks/**`; fixing that too is cheap.
- **`postman/gamified-tracker.postman_collection.json`** — a new `Analytics` folder inserted after the
  `Activity Log` folder (it depends on logs existing), plus updating the run-order sentence in
  `info.description`. Match the idiom: `pm.test('Status is 200', …)` first, `var json =
  pm.response.json();` hoisted once, `raw` + split `host`/`path` URLs with a `query` array.

---

## Known simplifications (call out; not bugs to fix now)

- **Per-category results are sparse, not zero-filled** — unlike the time series. A category with no
  activity is simply absent rather than returned as `0`. Deliberate: a continuous series matters for
  charting a time axis, but six always-present category rows would be noise in a "what did I spend time
  on" summary. `RankServiceImpl.getDistribution()` takes the opposite view for rank tiers; both are
  defensible, this is the asymmetry and it is intentional.
- **`totalMinutes` on the weekly report is summed from the category breakdown**, not queried separately —
  correct, but it silently couples the two numbers.
- **No pagination.** The 366-day cap bounds the response instead. Consistent with the rest of the repo,
  which never returns `Page<T>`.
- **The 366-day cap is arbitrary** and not configurable. If it ever needs tuning, follow
  `ranking.recompute-interval-ms` and externalize it to `application.yaml`.
- **`xpChangeVsPreviousWeek` is an absolute delta, not a percentage** — a percentage would need a
  divide-by-zero guard for a user's first week.

## Verification

1. **`mvn -pl activity-service -am clean verify`** — all green. `ActivityLogRepositoryTest` is the one that
   matters: it proves the HQL date functions translate on H2.
2. **Postgres check — the gap the H2 tests cannot close.** `docker compose up -d`, then exercise each
   endpoint against the real Postgres. **H2 is more permissive about `GROUP BY` than Postgres, so a green
   `@DataJpaTest` does not prove the query runs in production.** This step is not optional.
3. End-to-end: register/login for a token, `POST /api/activitylog` several times across different
   activities and categories, then:
   ```bash
   curl "$GW/api/analytics/xp-over-time?from=2026-07-01&to=2026-07-28&bucket=DAY" -H "Authorization: Bearer $T"
   curl "$GW/api/analytics/xp-over-time?from=2026-07-01&to=2026-07-28&bucket=WEEK" -H "Authorization: Bearer $T"
   curl "$GW/api/analytics/by-category" -H "Authorization: Bearer $T"
   curl "$GW/api/analytics/weekly-report" -H "Authorization: Bearer $T"
   ```
   Expect: a continuous daily series with explicit `0` rows on idle days; WEEK buckets whose `bucketStart`
   is always a Monday; categories ordered by XP desc; a weekly report whose `daily` array sums to `totalXp`.
4. **Gateway routing:** the same calls must work through `:8080` and return `401` without a token.
5. **Identity is header-driven:** a request authenticated as user A but forging `userId: B` must return
   **A's** data — `JwtFilter` overwrites the header.
6. **Error path:** `?from=2026-07-28&to=2026-07-01` must return `400` with a `ProblemDetail` body, not a 500.

## Out of scope / follow-ups

- **File a separate issue:** `ActivityLogRequest.startTime` is `@FutureOrPresent` and `endTime` is
  `@Future` — backwards for a *log*, since you record what you **did**. Fixing it to `@PastOrPresent` would
  make `start_time` the semantically correct analytics axis, a one-line change in `sumXpByDay`.
- Per-activity (rather than per-category) breakdown; CSV/export; cross-user or admin-wide analytics.
- Caching / materialization. If these queries get hot, `RankRecomputeServiceImpl`'s `@Scheduled` + native
  upsert is the in-repo precedent.
- `CONTRIBUTING.md` (on `main`) mandates a `docs/features/*.md` entry for new features, but **`docs/` does
  not exist on `main`** — only on the unmerged `docs` branch. Same for the `DESIGN_PATTERNS.md` it
  references. Write the feature doc only if it's being staged for that branch.
- `CONTRIBUTING.md` also mandates `feature/<desc>` branch naming and Conventional Commits squashed to a
  single commit. The branch `17-add-analytics-endpoints` predates that rule — flag, don't rename.
