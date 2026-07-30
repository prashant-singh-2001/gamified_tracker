# TODO — Session Integrity: bound activity-log durations and quarantine suspicious XP (issue #67)

> Exact code for every step. Branch `67-detect-anomalousimplausible-activity-log-durations-session-integrity`
> already exists and is clean. **Status: not yet implemented.** This is a plan, not a record of work done.

## Context

XP is linear in `durationMinutes`, the global leaderboard is `SUM(level_tracker.total_xp) GROUP BY user_id`,
and **nothing anywhere bounds duration**. That makes the ranking economy trivially farmable. There are two
distinct gaps, and the first one is worse than issue #67's original description.

**Gap 1 — `endTime` is completely unvalidated.** `ActivityLogRequest` carries `@PastOrPresent` on
`startTime` but only `@NotNull` on `endTime`. The sole additional check is `endTime.isAfter(startTime)` in
`ActivityLogServiceImpl`. So `startTime = now, endTime = now + 10 years` is accepted, yields a
~5.2-million-minute duration, and awards effectively unbounded XP from a single request. This is a live
exploit, not a statistical edge case. (Issue #67 has been amended to correct an inverted premise here — see
the issue history.)

**Gap 2 — no notion of an implausible session.** Even with sane timestamps, a 20-hour "Study" block is
accepted as-is. There is no cap, no outlier detection, and no way to withhold or review an award.

**Outcome:** a three-layer ladder — validate, cap, then flag — where flagged logs are persisted and visible
but their XP is *quarantined* until a maintainer approves it. The leaderboard can't be poisoned, and a false
positive costs the user a review rather than their XP.

### Why XP has to be stopped *before* the event is published

`LevelTrackerServiceImpl.save()` does `tracker.setTotalXp(tracker.getTotalXp() + dto.xp())` on a single
mutated-in-place row. There is **no per-log XP ledger in gamification-service**, no decrement path anywhere
in the repo, and `LevelTrackerRequestDTO.xp` is `@PositiveOrZero`, so a negative clawback is blocked at the
DTO. `processed_event`'s PK is the logId, so once consumed a log can never be replayed.

| Stage | Reversible? |
|---|---|
| Inside `addActivityLogResponseResponseEntity`, pre-commit | Yes — throw, transaction rolls back |
| `activity_log` + `outbox_event` rows committed | The outbox row is a promise nothing can retract |
| `OutboxRelay` publishes → listener inserts `processed_event` | No — replay permanently blocked |
| **`setTotalXp(getTotalXp() + xp)`** | **No. Point of no return.** |

So the only cheap seam is the outbox write itself. **A flagged log simply does not get an outbox row.**
Approval writes the row later, in a transaction, and the existing 2-second relay awards the XP naturally.

- `OutboxRelay` and **all of gamification-service are untouched** by this change.
- The existing `uk_outbox_event_idempotency_key` unique constraint on `idempotency_key = logId` prevents a
  double-award for free if approval is somehow called twice.
- Rejection is simply "never write the row" — no compensation logic exists or is needed.

## The three layers

| Layer | Mechanism | On violation |
|---|---|---|
| **0 — Validation** | `@PastOrPresent` on `endTime` | 400 via the existing `MethodArgumentNotValidException` handler |
| **1 — Hard cap** | `durationMinutes > max-duration-minutes` | **Reject, 400.** A 30-hour session is impossible input, same class as the existing `endTime.isAfter(startTime)` guard |
| **2 — Statistical** | One-sided modified z-score (MAD) per `(user, category)` | **Flag + quarantine XP**, pending maintainer review |

Layer 0 is a behavior change and must be called out in the PR: clients can no longer log in-progress or
future sessions. That is correct for a *log* — you record what you did — and it closes Gap 1.

## Repo conventions to honor

- **Comment out replaced lines, don't delete them.**
- **Do not commit** — the maintainer commits. This doc stays **untracked**.
- Services return `ResponseEntity<...>` directly from the service layer — controllers contain zero logic.
- DTOs are **records, no Lombok**. `ActivityLog`/`Activity`/`ActivityStreak`/`OutboxEvent` entities use
  Lombok `@Getter @Setter @Builder`.
- Exceptions take domain values and build their own message (see `InactiveActivityException`); one-liner
  `ProblemDetail.forStatusAndDetail(...)` handlers, never `ResponseEntity<ProblemDetail>`.
- `@AllArgsConstructor` + `private final` fields — no `@Autowired`, no hand-written constructors.

---

## Step 1 — Migration `V4__add_activity_log_review_status.sql`

`activity-service/src/main/resources/db/migration/`. `ddl-auto: validate`, so this lands before the entity
change. Style follows `V3__create_activity_streak.sql`.

```sql
-- Session integrity (issue #67): quarantine state for suspicious activity logs.
-- CLEARED  = passed all checks; XP awarded normally (the default for every existing row)
-- FLAGGED  = statistical outlier; NO outbox row was written, so XP is withheld pending review
-- APPROVED = a maintainer cleared it; the outbox row was written at approval time
-- REJECTED = a maintainer rejected it; XP is never awarded
-- NOT NULL DEFAULT backfills every pre-existing row as CLEARED in one statement.
ALTER TABLE activity_log
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'CLEARED';

-- Drives the admin review queue (WHERE review_status = 'FLAGGED' ORDER BY created_at DESC).
CREATE INDEX idx_activity_log_review_status
    ON activity_log (review_status);

-- The detector's baseline query is
--   WHERE user_id = ? AND created_at ... ORDER BY created_at DESC
-- and activity_log currently has only idx_activity_log_user_id on (user_id) alone.
CREATE INDEX idx_activity_log_user_id_created_at
    ON activity_log (user_id, created_at);
```

> **V-number collision.** `V4` is also wanted by the analytics work (PR #59 / `ANALYTICS_TODO.md`) and the
> AI-insights plan. Whichever lands second renumbers to `V5` and drops any duplicate
> `idx_activity_log_user_id_created_at`.

## Step 2 — `ReviewStatus` enum + `ActivityLog` entity field

### `dao/ReviewStatus.java` (new)

```java
package com.tracker.activity.dao;

public enum ReviewStatus {
    CLEARED,
    FLAGGED,
    APPROVED,
    REJECTED
}
```

### `dao/ActivityLog.java` (modified — full file)

```java
package com.tracker.activity.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue
    private Long id;

    private Long userId; // from auth service

    @ManyToOne
    private Activity activity;

    @NotNull(message = "Start Time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    private Long durationMinutes;

    // Gamification snapshot
    private double xpEarned;

    // Optional
    private String notes;

    private LocalDateTime createdAt;

    // Session integrity (#67): CLEARED by default. FLAGGED withholds the outbox row (see
    // ActivityLogServiceImpl) until a maintainer approves/rejects via ActivityLogReviewService.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.CLEARED;
}
```

`@Builder.Default` is load-bearing — `ActivityLog` is built via Lombok `@Builder` in `mapToActivityLog` and
in every test fixture; without it the field is silently `null` and the `nullable = false` column blows up
at flush.

## Step 3 — `ActivityLogRequest` validation (closes Gap 1)

### `dto/ActivityLogRequest.java` (modified — full file)

```java
package com.tracker.activity.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record ActivityLogRequest(
        @NotBlank(message = "Activity name is required")
        String activityName,

        @NotNull(message = "Start time is required")
        @PastOrPresent(message = "start Time should be past or present")
        LocalDateTime startTime,

        // Session integrity (#67), closes Gap 1: previously unbounded, which let
        // startTime=now, endTime=now+10y through and award ~5.2M minutes of XP.
        @NotNull(message = "End time is required")
        @PastOrPresent(message = "end time should be past or present")
        LocalDateTime endTime,

        String notes,
        LocalDateTime createdAt
) {
}
```

## Step 4 — `DurationOutlierDetector` (pure domain, no Spring)

New package `activity-service/.../domain/`. Pattern copied from gamification-service's `LevelCurve` /
`LevelProgress`. No Spring or JPA dependency — this is what makes the statistics exhaustively unit-testable.

Iglewicz–Hoaglin modified z-score: `M = 0.6745 * (x - median) / MAD`, flagged above the configured
threshold. Four properties matter more than the formula: it's **one-sided** (only sessions *longer* than
the baseline are a threat); `MAD == 0` falls back to mean absolute deviation rather than dividing by zero;
if *that* is also zero (every prior sample identical) it falls back to a relative multiple of the median;
and below `minSamples` priors it abstains rather than guessing on thin evidence.

### `domain/DurationOutlierDetector.java` (new)

```java
package com.tracker.activity.domain;

import java.util.List;

/**
 * Pure statistics — no Spring, no JPA — so the outlier math is exhaustively unit-testable
 * without a database. One-sided modified z-score (Iglewicz & Hoaglin): only sessions LONGER
 * than the baseline are ever flagged, since a suspiciously short session is not a threat.
 */
public class DurationOutlierDetector {

    private static final double Z_TO_MAD_CONSTANT = 0.6745;
    // Consistency constant relating mean absolute deviation to standard deviation for a
    // normal distribution — used only when MAD is zero (see the meanAD fallback below).
    private static final double Z_TO_MEAN_AD_CONSTANT = 1.253314;

    public enum Basis {
        PER_USER_CATEGORY, GLOBAL_CATEGORY, NONE
    }

    public record Verdict(boolean flagged, double modifiedZScore, double median, int sampleSize, Basis basis) {
        public static Verdict abstain() {
            return new Verdict(false, 0.0, 0.0, 0, Basis.NONE);
        }
    }

    private final double threshold;
    private final int minSamples;
    private final double relativeFactor;

    public DurationOutlierDetector(double threshold, int minSamples, double relativeFactor) {
        this.threshold = threshold;
        this.minSamples = minSamples;
        this.relativeFactor = relativeFactor;
    }

    /**
     * Scores {@code candidate} against {@code priorDurations} (this user+category's
     * CLEARED/APPROVED baseline). Falls back to {@code globalDurations} (same category, all
     * users) when the personal sample is too thin, and abstains entirely below minSamples even
     * globally — a cold-start user should never be flagged on thin evidence.
     */
    public Verdict evaluate(long candidate, List<Long> priorDurations, List<Long> globalDurations) {
        if (priorDurations.size() >= minSamples) {
            return score(candidate, priorDurations, Basis.PER_USER_CATEGORY);
        }
        if (globalDurations.size() >= minSamples) {
            return score(candidate, globalDurations, Basis.GLOBAL_CATEGORY);
        }
        return Verdict.abstain();
    }

    private Verdict score(long candidate, List<Long> samples, Basis basis) {
        double median = median(samples);

        // One-sided: a short session relative to the baseline is never suspicious.
        if (candidate <= median) {
            return new Verdict(false, 0.0, median, samples.size(), basis);
        }

        double mad = medianAbsoluteDeviation(samples, median);
        double modifiedZ;

        if (mad > 0) {
            modifiedZ = Z_TO_MAD_CONSTANT * (candidate - median) / mad;
        } else {
            double meanAd = meanAbsoluteDeviation(samples, median);
            if (meanAd > 0) {
                // MAD is zero (>= half the sample equals the median) but the sample isn't
                // perfectly uniform — fall back to mean absolute deviation, as Iglewicz &
                // Hoaglin prescribe for this case.
                modifiedZ = (candidate - median) / (Z_TO_MEAN_AD_CONSTANT * meanAd);
            } else {
                // Every prior sample is identical (zero dispersion). No z-score is meaningful;
                // fall back to a plain relative multiple of the median.
                boolean flagged = median > 0 && candidate > median * relativeFactor;
                return new Verdict(flagged, 0.0, median, samples.size(), basis);
            }
        }

        return new Verdict(modifiedZ > threshold, modifiedZ, median, samples.size(), basis);
    }

    private static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 0
                ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
                : sorted.get(n / 2);
    }

    private static double medianAbsoluteDeviation(List<Long> values, double median) {
        List<Double> deviations = values.stream().map(v -> Math.abs(v - median)).sorted().toList();
        int n = deviations.size();
        return n % 2 == 0
                ? (deviations.get(n / 2 - 1) + deviations.get(n / 2)) / 2.0
                : deviations.get(n / 2);
    }

    private static double meanAbsoluteDeviation(List<Long> values, double median) {
        return values.stream().mapToDouble(v -> Math.abs(v - median)).average().orElse(0.0);
    }
}
```

## Step 5 — Config

`api-gateway`'s `RateLimitProperties` is the repo-wide precedent for a typed `@ConfigurationProperties`
record; activity-service currently only uses `@Value` (`outbox.relay.delay-ms`), but six related knobs
warrant the typed-record pattern instead.

### `config/SessionIntegrityProperties.java` (new)

```java
package com.tracker.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "session-integrity")
public record SessionIntegrityProperties(
        boolean outlierDetectionEnabled,
        long maxDurationMinutes,
        double modifiedZThreshold,
        int minSamples,
        int baselineWindow,
        double relativeFactor) {
}
```

### `config/SessionIntegrityConfig.java` (new)

```java
package com.tracker.activity.config;

import com.tracker.activity.domain.DurationOutlierDetector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionIntegrityProperties.class)
public class SessionIntegrityConfig {

    @Bean
    public DurationOutlierDetector durationOutlierDetector(SessionIntegrityProperties props) {
        return new DurationOutlierDetector(props.modifiedZThreshold(), props.minSamples(), props.relativeFactor());
    }
}
```

### `application.yaml` addition

```yaml
# Session integrity (issue #67). Layer 1 (max-duration-minutes) is a hard reject; layer 2
# (the outlier detector) only quarantines XP pending review.
session-integrity:
  # Kill switch for layer 2 only — the hard cap always applies. Mirrors the
  # leveling.default-curve.enabled convention in gamification-service.
  outlier-detection-enabled: ${SESSION_INTEGRITY_OUTLIER_ENABLED:true}
  max-duration-minutes: ${SESSION_INTEGRITY_MAX_DURATION:1440}
  modified-z-threshold: ${SESSION_INTEGRITY_Z_THRESHOLD:3.5}
  min-samples: ${SESSION_INTEGRITY_MIN_SAMPLES:10}
  baseline-window: ${SESSION_INTEGRITY_BASELINE_WINDOW:100}
  relative-factor: ${SESSION_INTEGRITY_RELATIVE_FACTOR:3.0}
```

`1440` = 24h. Deliberately generous: layer 1 exists to reject the impossible, not to police the unusual.
Add all six `SESSION_INTEGRITY_*` vars to `.env.example`.

## Step 6 — Repository queries (**the security-critical step**)

First `@Query` and first `Pageable` in activity-service. `Pageable` is used purely as a limiter returning
`List<T>` — this repo returns `Page<T>` nowhere.

### `repository/ActivityLogRepository.java` (modified — full file)

```java
package com.tracker.activity.repository;

import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByUserId(Long userId);

    // Session integrity (#67): duration baseline for the outlier detector, scoped to one user.
    // SECURITY: only CLEARED and APPROVED logs may form the baseline. Including FLAGGED or
    // REJECTED rows would let a farmer poison their own baseline — submit a run of huge
    // sessions, and the median they shift makes every subsequent huge session look normal.
    // Category lives on the joined Activity, not on activity_log.
    @Query("""
            SELECT l.durationMinutes FROM ActivityLog l
            JOIN l.activity a
            WHERE l.userId = :userId
              AND a.category = :category
              AND l.durationMinutes IS NOT NULL
              AND l.reviewStatus IN :baselineStatuses
            ORDER BY l.createdAt DESC
            """)
    List<Long> findRecentDurationsForUserAndCategory(@Param("userId") Long userId,
                                                      @Param("category") Category category,
                                                      @Param("baselineStatuses") Collection<ReviewStatus> baselineStatuses,
                                                      Pageable limit);

    // Cold-start fallback: same query, all users. Used only when the per-user sample is
    // below session-integrity.min-samples. Same poisoning guard applies.
    @Query("""
            SELECT l.durationMinutes FROM ActivityLog l
            JOIN l.activity a
            WHERE a.category = :category
              AND l.durationMinutes IS NOT NULL
              AND l.reviewStatus IN :baselineStatuses
            ORDER BY l.createdAt DESC
            """)
    List<Long> findRecentDurationsForCategory(@Param("category") Category category,
                                              @Param("baselineStatuses") Collection<ReviewStatus> baselineStatuses,
                                              Pageable limit);

    // Admin review queue.
    List<ActivityLog> findByReviewStatusOrderByCreatedAtDesc(ReviewStatus reviewStatus, Pageable page);
}
```

> The global fallback is itself poisonable in aggregate if many users farm the same category. Acceptable at
> this scale; a trimmed mean or admin-curated per-category baseline is a follow-up, not a blocker.

## Step 7 — Exceptions + handler

### `exception/ImplausibleSessionException.java` (new)

```java
package com.tracker.activity.exception;

/**
 * Thrown when a logged session's duration exceeds the hard sanity cap (layer 1 of session
 * integrity, issue #67) — an impossible session, not merely an unusual one. Statistically
 * unusual-but-possible sessions are handled separately, by flagging for review rather than
 * rejecting outright.
 */
public class ImplausibleSessionException extends RuntimeException {

    public ImplausibleSessionException(long durationMinutes, long maxDurationMinutes) {
        super("Session duration of " + durationMinutes + " minutes exceeds the maximum allowed ("
                + maxDurationMinutes + " minutes).");
    }
}
```

### `exception/InvalidReviewStateException.java` (new)

```java
package com.tracker.activity.exception;

import com.tracker.activity.dao.ReviewStatus;

/**
 * Thrown when approve/reject is called on a log that isn't currently FLAGGED — guards against
 * re-approving an already-APPROVED log into a second XP award.
 */
public class InvalidReviewStateException extends RuntimeException {

    public InvalidReviewStateException(Long logId, ReviewStatus currentStatus) {
        super("Activity log " + logId + " is not pending review (current status: " + currentStatus + ").");
    }
}
```

### `exception/GlobalExceptionHandler.java` (modified — full file)

```java
package com.tracker.activity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ActivityNotFoundException.class)
    public ProblemDetail handleNotFound(ActivityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InactiveActivityException.class)
    public ProblemDetail handleInactiveActivity(InactiveActivityException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    public ProblemDetail handleTimeout(InvalidTimeRangeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Session integrity (#67): a session past the hard cap is impossible input, same class as
    // InvalidTimeRangeException above.
    @ExceptionHandler(ImplausibleSessionException.class)
    public ProblemDetail handleImplausibleSession(ImplausibleSessionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Session integrity (#67): reviewing a log that isn't FLAGGED is a state conflict, same
    // class as InactiveActivityException above.
    @ExceptionHandler(InvalidReviewStateException.class)
    public ProblemDetail handleInvalidReviewState(InvalidReviewStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // since @Valid can throw MethodArgumentNotValidException instead of custom exception
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
```

## Step 8 — Wire layers 1 and 2 into `ActivityLogServiceImpl`

### `service/impl/ActivityLogServiceImpl.java` (modified — full file)

```java
package com.tracker.activity.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.config.SessionIntegrityProperties;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.ActivityStreak;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.StreakResponse;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.ImplausibleSessionException;
import com.tracker.activity.exception.InactiveActivityException;
import com.tracker.activity.exception.InvalidTimeRangeException;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.repository.ActivityStreakRepository;
import com.tracker.activity.service.ActivityLogService;
import com.tracker.contracts.event.ActivityLoggedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


@AllArgsConstructor
@Service
public class ActivityLogServiceImpl implements ActivityLogService {

    // Session integrity (#67): only logs that passed review may seed the outlier baseline —
    // including FLAGGED/REJECTED rows would let a farmer poison their own baseline.
    private static final Set<ReviewStatus> BASELINE_STATUSES = Set.of(ReviewStatus.CLEARED, ReviewStatus.APPROVED);

    private final ActivityLogRepository activityLogRepository;
    private final ActivityRepository activityRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ActivityStreakRepository activityStreakRepository;
    private final DurationOutlierDetector durationOutlierDetector;
    private final SessionIntegrityProperties sessionIntegrityProperties;
    private final MeterRegistry meterRegistry;

    @Override
    public ResponseEntity<ActivityLogResponse> getActivityLogResponseEntity(Long id) {
        var activityLog = activityLogRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException("Activity log not found: " + id));

        // historical logs don't have bonus/leveled flags stored yet — return defaults
        return ResponseEntity.ok(mapToActivityLogResponse(activityLog, false, 1.0, false, 0, 1.0));
    }

    @Override
    @Transactional
    public ResponseEntity<ActivityLogResponse> addActivityLogResponseResponseEntity(
            Long userId, ActivityLogRequest activityLogRequest) {

        // fail fast on bad input before it can produce a negative duration / negative XP
        if (!activityLogRequest.endTime().isAfter(activityLogRequest.startTime())) {
            throw new InvalidTimeRangeException("endTime must be after startTime");
        }

        var activityLog = mapToActivityLog(userId, activityLogRequest);
        long durationMinutes = Duration.between(activityLog.getStartTime(), activityLog.getEndTime()).toMinutes();

        // Session integrity (#67) layer 1: a hard sanity cap. This is impossible input, not
        // ambiguous input — same class as the endTime.isAfter(startTime) guard above — so it
        // rejects outright rather than flagging for review.
        if (durationMinutes > sessionIntegrityProperties.maxDurationMinutes()) {
            meterRegistry.counter("activity.log.rejected", "reason", "duration_cap").increment();
            throw new ImplausibleSessionException(durationMinutes, sessionIntegrityProperties.maxDurationMinutes());
        }

        activityLog.setDurationMinutes(durationMinutes);
        activityLog.setUserId(userId);

        // ThreadLocalRandom avoids the pre-existing RandomGenerator.getDefault() "L32X64MixRandom"
        // failure (bug #2) that 500s this endpoint on some JVM/container images.
        var random = ThreadLocalRandom.current();

        LocalDate activityDate = activityLog.getStartTime().toLocalDate();
        ActivityStreak streak = applyStreak(userId, activityLog.getActivity().getId(), activityDate);
        double streakMult = streakMultiplier(streak.getCurrentStreak());

        // Source of truth (#10): per-activity override when set (> 0), else the Category base.
        double multiplier = activityLog.getActivity().effectiveXpMultiplier();
        double bonus = random.nextDouble() < 0.2 ? random.nextDouble(1.1, 1.5) : 1.0;
        activityLog.setXpEarned(activityLog.getDurationMinutes() * multiplier * bonus * streakMult);

        // Session integrity (#67) layer 2: statistical outlier check. A flagged log still gets
        // its streak and xpEarned computed and frozen normally — only its magnitude is in
        // doubt, not whether the session happened — so approval later awards exactly this
        // amount. See evaluateSessionIntegrity for the baseline query and poisoning guard.
        boolean flagged = evaluateSessionIntegrity(userId, activityLog, durationMinutes);
        activityLog.setReviewStatus(flagged ? ReviewStatus.FLAGGED : ReviewStatus.CLEARED);

        // 1) persist the log FIRST (fixes #4) — the generated id is our logId / idempotency key
        var saved = activityLogRepository.save(activityLog);

        // 2) SAME transaction: write the outbox row (atomic with the log insert) — UNLESS this
        // log was flagged, in which case XP must be withheld until a maintainer reviews it.
        // Approval (ActivityLogReviewServiceImpl) writes this same shape of row later.
        if (!flagged) {
            var event = new ActivityLoggedEvent(
                    saved.getId(), userId, saved.getActivity().getId(), saved.getXpEarned());
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("ActivityLog")
                    .aggregateId(saved.getId())
                    .eventType("ActivityLogged")
                    .payload(toJson(event))
                    .idempotencyKey(String.valueOf(saved.getId()))
                    .createdAt(LocalDateTime.now())
                    .publishedAt(null)
                    .build());
        }

        boolean bonusApplied = bonus != 1.0;
        // leveledUp is now EVENTUAL (XP applied async by the consumer) -> false at write time
        return ResponseEntity.ok(mapToActivityLogResponse(saved, bonusApplied, bonus, false, streak.getCurrentStreak(), streakMult));
    }

    /**
     * Session integrity (#67) layer 2. Returns true when the session should be flagged.
     * Baseline is this user's own (CLEARED/APPROVED) history for the same category; falls back
     * to the global per-category baseline when the personal sample is too thin, and abstains
     * entirely below session-integrity.min-samples even globally.
     */
    private boolean evaluateSessionIntegrity(Long userId, ActivityLog activityLog, long durationMinutes) {
        if (!sessionIntegrityProperties.outlierDetectionEnabled()) {
            return false;
        }

        var category = activityLog.getActivity().getCategory();
        var window = PageRequest.of(0, sessionIntegrityProperties.baselineWindow());

        List<Long> priorDurations = activityLogRepository.findRecentDurationsForUserAndCategory(
                userId, category, BASELINE_STATUSES, window);
        List<Long> globalDurations = priorDurations.size() >= sessionIntegrityProperties.minSamples()
                ? List.of() // personal sample is sufficient; skip the extra query
                : activityLogRepository.findRecentDurationsForCategory(category, BASELINE_STATUSES, window);

        var verdict = durationOutlierDetector.evaluate(durationMinutes, priorDurations, globalDurations);

        if (verdict.flagged()) {
            meterRegistry.counter("activity.log.flagged", "category", category.name()).increment();
        }
        return verdict.flagged();
    }

    @Override
    public ResponseEntity<List<StreakResponse>> getStreaksForUser(Long userId) {
        var streaks = activityStreakRepository.findByUserId(userId).stream().map(
                s -> new StreakResponse(s.getActivityId(), s.getCurrentStreak(), s.getLongestStreak(), s.getLastActivityDate())
        ).toList();
        return ResponseEntity.ok(streaks);
    }

    private String toJson(ActivityLoggedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ActivityLoggedEvent", e);
        }
    }

    public ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(Long id) {
        var activityLogList = activityLogRepository.findByUserId(id);

        var activityLogResponses = activityLogList.stream().map(a -> mapToActivityLogResponse(a, false, 1.0, false, 0, 1.0)).toList();

        return ResponseEntity.ok(activityLogResponses);
    }

    private ActivityLog mapToActivityLog(Long userId, ActivityLogRequest activityLogRequest) {
        var activity = activityRepository.findByName(activityLogRequest.activityName())
                .orElseThrow(() -> new ActivityNotFoundException(
                        "Activity not found: " + activityLogRequest.activityName()));

        // Issue #7: reject log attempts against soft-deleted activities before any
        // XP or streak side-effects are applied.
        if (!activity.isActive()) {
            throw new InactiveActivityException(activityLogRequest.activityName());
        }

        return ActivityLog.builder()
                .userId(userId)
                .activity(activity)
                .startTime(activityLogRequest.startTime())
                .endTime(activityLogRequest.endTime())
                .notes(activityLogRequest.notes())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActivityLogResponse mapToActivityLogResponse(ActivityLog activityLog, boolean bonusApplied, double bonusMultiplier, boolean leveledUp, int currentStreak, double streakMult) {
        return new ActivityLogResponse(
                activityLog.getId(),
                activityLog.getUserId(),
                activityLog.getActivity(),
                activityLog.getStartTime(),
                activityLog.getEndTime(),
                activityLog.getDurationMinutes(),
                activityLog.getXpEarned(),
                activityLog.getNotes(),
                activityLog.getCreatedAt(),
                bonusApplied,
                bonusMultiplier,
                leveledUp,
                currentStreak,
                streakMult,
                activityLog.getReviewStatus()
        );
    }

    private ActivityStreak applyStreak(Long userId, Long activityId, LocalDate activityDate) {
        ActivityStreak streak = activityStreakRepository.findByUserIdAndActivityId(userId, activityId)
                .orElseGet(() -> ActivityStreak.builder()
                        .userId(userId)
                        .activityId(activityId)
                        .currentStreak(0)
                        .longestStreak(0)
                        .lastActivityDate(null)
                        .build());
        LocalDate last = streak.getLastActivityDate();
        if (last == null) {
            streak.setCurrentStreak(1);
            streak.setLastActivityDate(activityDate);
        } else {
            long gap = ChronoUnit.DAYS.between(last, activityDate);
            if (gap == 1) {
                streak.setCurrentStreak(streak.getCurrentStreak() + 1);
                streak.setLastActivityDate(activityDate);
            } else if (gap > 1) {
                streak.setCurrentStreak(1);
                streak.setLastActivityDate(activityDate);
            }
        }
        streak.setLongestStreak(Math.max(streak.getLongestStreak(), streak.getCurrentStreak()));
        return activityStreakRepository.save(streak);
    }

    private double streakMultiplier(int currentStreak) {
        return 1.0 + Math.min(Math.max(currentStreak - 1, 0), 10) * 0.05;
    }
}
```

`MeterRegistry` needs no new dependency — `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
are hoisted onto every module in the root `pom.xml`, and Spring Boot auto-configures a `MeterRegistry` bean
from them.

## Step 9 — Surface the status to the user

### `dto/ActivityLogResponse.java` (modified — full file)

```java
package com.tracker.activity.dto;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ReviewStatus;

import java.time.LocalDateTime;

public record ActivityLogResponse(
        Long id,
        Long userId,
        Activity activity,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMinutes,
        double xpEarned,
        String notes,
        LocalDateTime createdAt,
        boolean bonusApplied,
        double bonusMultiplier,
        boolean leveledUp,
        int currentStreak,
        double streakMultiplier,
        ReviewStatus reviewStatus
) {
}
```

Silently withholding XP with no signal is worse than the problem being solved — the user needs to see
"pending review". This touches `mapToActivityLogResponse` (Step 8, already updated) and every construction
site of `ActivityLogResponse` in `ActivityLogControllerTest` and `ActivityLogServiceImplTest` (Step 12 adds
the 15th argument — `ReviewStatus.CLEARED` for every existing fixture).

## Step 10 — Admin review API

### `dto/FlaggedLogDto.java` (new)

```java
package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;

import java.time.LocalDateTime;

public record FlaggedLogDto(
        Long id,
        Long userId,
        String activityName,
        Category category,
        Long durationMinutes,
        double xpEarned,
        LocalDateTime createdAt,
        ReviewStatus reviewStatus
) {
}
```

### `service/ActivityLogReviewService.java` (new)

```java
package com.tracker.activity.service;

import com.tracker.activity.dto.FlaggedLogDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ActivityLogReviewService {

    ResponseEntity<List<FlaggedLogDto>> getFlaggedLogs();

    ResponseEntity<FlaggedLogDto> approve(Long id);

    ResponseEntity<FlaggedLogDto> reject(Long id);
}
```

### `service/impl/ActivityLogReviewServiceImpl.java` (new)

```java
package com.tracker.activity.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.dto.FlaggedLogDto;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.InvalidReviewStateException;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.ActivityLogReviewService;
import com.tracker.contracts.event.ActivityLoggedEvent;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Service
public class ActivityLogReviewServiceImpl implements ActivityLogReviewService {

    private final ActivityLogRepository activityLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseEntity<List<FlaggedLogDto>> getFlaggedLogs() {
        var flagged = activityLogRepository.findByReviewStatusOrderByCreatedAtDesc(
                ReviewStatus.FLAGGED, PageRequest.of(0, 200));
        return ResponseEntity.ok(flagged.stream().map(this::mapToDto).toList());
    }

    @Override
    @Transactional
    public ResponseEntity<FlaggedLogDto> approve(Long id) {
        var log = requireFlagged(id);

        log.setReviewStatus(ReviewStatus.APPROVED);
        var saved = activityLogRepository.save(log);

        // The outbox row this log never got at write time (ActivityLogServiceImpl withheld it
        // because the log was flagged). idempotency_key is unique on logId, so a second
        // approve() call is refused by requireFlagged() above before this ever runs twice.
        var event = new ActivityLoggedEvent(
                saved.getId(), saved.getUserId(), saved.getActivity().getId(), saved.getXpEarned());
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("ActivityLog")
                .aggregateId(saved.getId())
                .eventType("ActivityLogged")
                .payload(toJson(event))
                .idempotencyKey(String.valueOf(saved.getId()))
                .createdAt(LocalDateTime.now())
                .publishedAt(null)
                .build());

        return ResponseEntity.ok(mapToDto(saved));
    }

    @Override
    @Transactional
    public ResponseEntity<FlaggedLogDto> reject(Long id) {
        var log = requireFlagged(id);
        log.setReviewStatus(ReviewStatus.REJECTED);
        return ResponseEntity.ok(mapToDto(activityLogRepository.save(log)));
    }

    private ActivityLog requireFlagged(Long id) {
        var log = activityLogRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException("Activity log not found: " + id));
        if (log.getReviewStatus() != ReviewStatus.FLAGGED) {
            throw new InvalidReviewStateException(id, log.getReviewStatus());
        }
        return log;
    }

    private String toJson(ActivityLoggedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ActivityLoggedEvent", e);
        }
    }

    private FlaggedLogDto mapToDto(ActivityLog log) {
        return new FlaggedLogDto(
                log.getId(),
                log.getUserId(),
                log.getActivity().getName(),
                log.getActivity().getCategory(),
                log.getDurationMinutes(),
                log.getXpEarned(),
                log.getCreatedAt(),
                log.getReviewStatus()
        );
    }
}
```

### `controller/ActivityLogReviewController.java` (new)

```java
package com.tracker.activity.controller;

import com.tracker.activity.dto.FlaggedLogDto;
import com.tracker.activity.service.ActivityLogReviewService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Maintainer-only. Enforced at the gateway (SecurityConfig: /api/activitylog/review/** ->
// hasRole("ADMIN")) — activity-service has no security dependency of its own and fully
// trusts the gateway, matching the existing POST /activity precedent.
@AllArgsConstructor
@RestController
@RequestMapping("/activitylog/review")
public class ActivityLogReviewController {

    private final ActivityLogReviewService activityLogReviewService;

    @GetMapping("/flagged")
    public ResponseEntity<List<FlaggedLogDto>> getFlaggedLogs() {
        return activityLogReviewService.getFlaggedLogs();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<FlaggedLogDto> approve(@PathVariable("id") Long id) {
        return activityLogReviewService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<FlaggedLogDto> reject(@PathVariable("id") Long id) {
        return activityLogReviewService.reject(id);
    }
}
```

`@RequestMapping("/activitylog/review")` rides the **existing** `/api/activitylog/**` gateway predicate —
no `RouteConfiguration` change, and it inherits the activity rate-limit bucket.

## Step 11 — Admin authorization at the gateway

activity-service has **no** `spring-boot-starter-security` at all, and the gateway forwards only `userId` —
the `role` claim becomes a `SimpleGrantedAuthority` at the gateway and is never propagated. So
authorization goes where the existing precedent puts it: `api-gateway/.../security/SecurityConfig.java`,
**above** `.anyRequest().authenticated()`.

### `security/SecurityConfig.java` (modified — relevant excerpt)

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**", "/swagger-ui.html", "/swagger-ui/**",
                "/v3/api-docs", "/v3/api-docs/**", "/swagger-resources/**", "/actuator/**")
        .permitAll()
        .requestMatchers(HttpMethod.POST, "/api/activity", "/api/activity/").hasRole("ADMIN")
        // Session integrity (#67): the review queue and its approve/reject transitions are
        // maintainer-only. Must precede anyRequest() below — matcher order matters.
        .requestMatchers("/api/activitylog/review/**").hasRole("ADMIN")
        .anyRequest().authenticated())
```

**Pre-existing caveat, not fixed here:** activity-service is reachable directly on `:8081` and registers
with Eureka, so in-cluster traffic bypasses the gateway and this check. That's exactly as true of
`POST /activity` today — no new exposure class, but gateway-level role checks remain perimeter-only.

## Step 12 — Tests

Conventions: `@DisplayName` everywhere; `@DataJpaTest` with `@Autowired` **field** injection and a
`private static` builder fixture; JUnit 5 `Assertions.*` (AssertJ is used nowhere in activity-service);
`@ExtendWith(MockitoExtension.class)` + `@Mock` for service tests, with a **real** `ObjectMapper` (existing
precedent in `ActivityLogServiceImplTest`) — extended here to a **real** `DurationOutlierDetector` and
`SessionIntegrityProperties`, and a real `SimpleMeterRegistry` (from `micrometer-core`, already on the
classpath transitively), rather than mocking pure/lightweight collaborators.

### 12a. `domain/DurationOutlierDetectorTest.java` (new) — the centre of gravity, pure unit, no Spring

```java
package com.tracker.activity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Duration Outlier Detector Tests")
class DurationOutlierDetectorTest {

    private final DurationOutlierDetector detector = new DurationOutlierDetector(3.5, 10, 3.0);

    private static List<Long> constant(long value, int count) {
        return Collections.nCopies(count, value);
    }

    @Test
    @DisplayName("abstains when the personal AND global sample are both below minSamples")
    void abstainsBelowMinSamples() {
        var verdict = detector.evaluate(600L, constant(60L, 5), constant(60L, 5));

        assertFalse(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.NONE, verdict.basis());
    }

    @Test
    @DisplayName("falls back to the global baseline when the personal sample is too thin")
    void fallsBackToGlobalBaseline() {
        List<Long> thinPersonal = constant(60L, 3);
        List<Long> sufficientGlobal = constant(60L, 20);

        var verdict = detector.evaluate(600L, thinPersonal, sufficientGlobal);

        assertEquals(DurationOutlierDetector.Basis.GLOBAL_CATEGORY, verdict.basis());
    }

    @Test
    @DisplayName("a session at or below the baseline median is never flagged (one-sided)")
    void shortOrEqualSessionNeverFlagged() {
        List<Long> priors = List.of(60L, 65L, 70L, 55L, 62L, 58L, 61L, 59L, 63L, 57L);

        assertFalse(detector.evaluate(10L, priors, List.of()).flagged());
        assertFalse(detector.evaluate(60L, constant(60L, 10), List.of()).flagged());
    }

    @Test
    @DisplayName("a session far beyond a dispersed baseline is flagged via the standard MAD path")
    void flagsClearOutlier_viaMad() {
        // Median 59.5, real dispersion so MAD > 0.
        List<Long> priors = List.of(55L, 58L, 60L, 61L, 62L, 59L, 63L, 57L, 64L, 56L);

        var verdict = detector.evaluate(600L, priors, List.of());

        assertTrue(verdict.flagged());
        assertTrue(verdict.modifiedZScore() > 3.5);
    }

    @Test
    @DisplayName("MAD == 0 (mostly identical) falls back to the mean-absolute-deviation path, not divide-by-zero")
    void zeroMad_fallsBackToMeanAbsoluteDeviation() {
        // 8 identical values + 2 outliers -> the MEDIAN of the deviations is 0, but the MEAN isn't.
        List<Long> priors = List.of(60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 65L, 90L);

        var verdict = detector.evaluate(600L, priors, List.of());

        assertTrue(verdict.flagged());
        assertEquals(60.0, verdict.median(), 1e-9);
    }

    @Test
    @DisplayName("every prior sample identical (zero dispersion) falls back to the relative-factor multiple")
    void zeroDispersion_fallsBackToRelativeFactor() {
        List<Long> priors = constant(60L, 10);

        // relativeFactor is 3.0 -> flag threshold is median * 3.0 = 180.
        assertFalse(detector.evaluate(179L, priors, List.of()).flagged());
        assertTrue(detector.evaluate(181L, priors, List.of()).flagged());
    }
}
```

### 12b. `ActivityLogServiceImplTest.java` — required updates + additions

**Required update:** the constructor call in `setUp()` grows from 5 to 8 arguments. Because the two new
repository methods return `List<Long>`, an unstubbed Mockito mock returns an **empty list by default**
(`ReturnsEmptyValues`), so `evaluate(duration, [], [])` abstains and every one of the 15 existing tests
keeps passing with **zero additional stubbing**.

```java
@BeforeEach
void setUp() {
    var detector = new DurationOutlierDetector(3.5, 10, 3.0);
    var properties = new SessionIntegrityProperties(true, 1440, 3.5, 10, 100, 3.0);
    var meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    activityLogService = new ActivityLogServiceImpl(
            activityLogRepository, activityRepository, outboxEventRepository, objectMapper,
            activityStreakRepository, detector, properties, meterRegistry);

    lenient().when(activityStreakRepository.save(any(ActivityStreak.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
}
```

Every existing `new ActivityLogResponse(...)` fixture (in this file and in `ActivityLogControllerTest`)
needs one more trailing argument: `ReviewStatus.CLEARED` (or `FLAGGED` where the test is about session
integrity specifically).

**New tests, appended to the file:**

```java
// ──────────────────────────────────────────────────────────────────────────
// Issue #67 — Session integrity
// ──────────────────────────────────────────────────────────────────────────

@Test
@DisplayName("a session over the hard cap is rejected before any persistence")
void addActivityLog_overHardCap_isRejected() {
    LocalDateTime start = LocalDateTime.now().minusDays(2);
    // 25 hours — over the 1440-minute (24h) default cap
    ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusHours(25), "notes", null);
    Activity activity = Activity.builder().id(7L).name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build();
    when(activityRepository.findByName("Study")).thenReturn(Optional.of(activity));

    assertThrows(ImplausibleSessionException.class,
            () -> activityLogService.addActivityLogResponseResponseEntity(1L, request));

    verifyNoInteractions(activityLogRepository);
    verifyNoInteractions(outboxEventRepository);
    verifyNoInteractions(activityStreakRepository);
}

@Test
@DisplayName("a session flagged by the outlier detector persists as FLAGGED and withholds the outbox row")
void addActivityLog_flaggedSession_withholdsOutboxRow() {
    Long userId = 1L;
    Activity activity = Activity.builder().id(7L).name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build();
    stubActivityAndSave(activity, 200L);

    // Ten prior 60-minute sessions establish a tight baseline.
    when(activityLogRepository.findRecentDurationsForUserAndCategory(
            eq(userId), eq(Category.STUDY), any(), any())).thenReturn(java.util.Collections.nCopies(10, 60L));

    LocalDateTime start = LocalDateTime.now().minusMinutes(600);
    // 600 minutes vs. a 60-minute baseline is a clear outlier, and still under the 1440 hard cap.
    ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(600), "notes", null);

    ActivityLogResponse body = activityLogService.addActivityLogResponseResponseEntity(userId, request).getBody();

    assertNotNull(body);
    assertEquals(ReviewStatus.FLAGGED, body.reviewStatus());
    verify(activityLogRepository).save(any(ActivityLog.class));
    // This is the assertion that matters: XP must NOT be published while flagged.
    verifyNoInteractions(outboxEventRepository);
}

@Test
@DisplayName("a session within the baseline is CLEARED and writes exactly one outbox row")
void addActivityLog_normalSession_isCleared() {
    Long userId = 1L;
    Activity activity = Activity.builder().id(7L).name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build();
    stubActivityAndSave(activity, 201L);

    when(activityLogRepository.findRecentDurationsForUserAndCategory(
            eq(userId), eq(Category.STUDY), any(), any())).thenReturn(java.util.Collections.nCopies(10, 60L));

    LocalDateTime start = LocalDateTime.now().minusMinutes(65);
    ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(65), "notes", null);

    ActivityLogResponse body = activityLogService.addActivityLogResponseResponseEntity(userId, request).getBody();

    assertNotNull(body);
    assertEquals(ReviewStatus.CLEARED, body.reviewStatus());
    verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
}

@Test
@DisplayName("outlier detection is skipped entirely when session-integrity.outlier-detection-enabled is false")
void addActivityLog_detectionDisabled_neverFlags() {
    var detector = new DurationOutlierDetector(3.5, 10, 3.0);
    var properties = new SessionIntegrityProperties(false, 1440, 3.5, 10, 100, 3.0); // kill switch off
    var disabledService = new ActivityLogServiceImpl(
            activityLogRepository, activityRepository, outboxEventRepository, objectMapper,
            activityStreakRepository, detector, properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

    Long userId = 1L;
    Activity activity = Activity.builder().id(7L).name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build();
    stubActivityAndSave(activity, 202L);
    // Even an extreme session vs. a tight baseline must not be flagged while disabled.
    when(activityLogRepository.findRecentDurationsForUserAndCategory(
            eq(userId), eq(Category.STUDY), any(), any())).thenReturn(java.util.Collections.nCopies(10, 60L));

    LocalDateTime start = LocalDateTime.now().minusMinutes(600);
    ActivityLogRequest request = new ActivityLogRequest("Study", start, start.plusMinutes(600), "notes", null);

    ActivityLogResponse body = disabledService.addActivityLogResponseResponseEntity(userId, request).getBody();

    assertEquals(ReviewStatus.CLEARED, body.reviewStatus());
    verify(outboxEventRepository).save(any(OutboxEvent.class));
}
```

### 12c. `service/ActivityLogReviewServiceImplTest.java` (new)

```java
package com.tracker.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.dto.FlaggedLogDto;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.InvalidReviewStateException;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.impl.ActivityLogReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Log Review Service Tests")
class ActivityLogReviewServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    private ActivityLogReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivityLogReviewServiceImpl(activityLogRepository, outboxEventRepository, objectMapper);
    }

    private static ActivityLog flaggedLog(Long id) {
        Activity activity = Activity.builder().id(7L).name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build();
        return ActivityLog.builder()
                .id(id).userId(1L).activity(activity)
                .durationMinutes(600L).xpEarned(900.0)
                .reviewStatus(ReviewStatus.FLAGGED)
                .build();
    }

    @Test
    @DisplayName("approve flips FLAGGED -> APPROVED and writes exactly one outbox row keyed by logId")
    void approve_writesOutboxRowOnce() {
        ActivityLog log = flaggedLog(50L);
        when(activityLogRepository.findById(50L)).thenReturn(Optional.of(log));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        FlaggedLogDto result = service.approve(50L).getBody();

        assertNotNull(result);
        assertEquals(ReviewStatus.APPROVED, result.reviewStatus());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        assertEquals("50", captor.getValue().getIdempotencyKey());
    }

    @Test
    @DisplayName("reject flips FLAGGED -> REJECTED and writes no outbox row")
    void reject_writesNoOutboxRow() {
        ActivityLog log = flaggedLog(51L);
        when(activityLogRepository.findById(51L)).thenReturn(Optional.of(log));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        FlaggedLogDto result = service.reject(51L).getBody();

        assertNotNull(result);
        assertEquals(ReviewStatus.REJECTED, result.reviewStatus());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @DisplayName("approving a log that is not FLAGGED is refused (blocks a double award)")
    void approve_nonFlaggedLog_isRefused() {
        ActivityLog alreadyApproved = flaggedLog(52L);
        alreadyApproved.setReviewStatus(ReviewStatus.APPROVED);
        when(activityLogRepository.findById(52L)).thenReturn(Optional.of(alreadyApproved));

        assertThrows(InvalidReviewStateException.class, () -> service.approve(52L));
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @DisplayName("reviewing a missing log throws ActivityNotFoundException")
    void review_missingLog_throwsNotFound() {
        when(activityLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> service.approve(999L));
    }
}
```

### 12d. `repository/ActivityLogRepositoryTest.java` (new) — proves the baseline query on H2 and the poisoning guard

```java
package com.tracker.activity.repository;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ActivityLogRepositoryTest {

    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private static final Set<ReviewStatus> BASELINE = Set.of(ReviewStatus.CLEARED, ReviewStatus.APPROVED);

    private ActivityLog log(Long userId, Activity activity, long duration, ReviewStatus status, LocalDateTime createdAt) {
        return activityLogRepository.save(ActivityLog.builder()
                .userId(userId).activity(activity)
                .startTime(createdAt).endTime(createdAt.plusMinutes(duration))
                .durationMinutes(duration).xpEarned(duration)
                .createdAt(createdAt).reviewStatus(status)
                .build());
    }

    @Test
    @DisplayName("findRecentDurationsForUserAndCategory excludes FLAGGED/REJECTED rows")
    void findRecentDurationsForUserAndCategory_excludesFlaggedAndRejected() {
        Activity study = activityRepository.save(Activity.builder().name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build());
        LocalDateTime now = LocalDateTime.now();

        log(1L, study, 60L, ReviewStatus.CLEARED, now.minusDays(1));
        log(1L, study, 65L, ReviewStatus.APPROVED, now.minusDays(2));
        // SECURITY regression: these must never appear in the baseline.
        log(1L, study, 9000L, ReviewStatus.FLAGGED, now.minusDays(3));
        log(1L, study, 9000L, ReviewStatus.REJECTED, now.minusDays(4));

        List<Long> baseline = activityLogRepository.findRecentDurationsForUserAndCategory(
                1L, Category.STUDY, BASELINE, PageRequest.of(0, 100));

        assertEquals(2, baseline.size());
        assertTrue(baseline.containsAll(List.of(60L, 65L)));
    }

    @Test
    void findRecentDurationsForUserAndCategory_doesNotLeakAnotherUsersRows() {
        Activity study = activityRepository.save(Activity.builder().name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build());
        LocalDateTime now = LocalDateTime.now();

        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        log(2L, study, 999L, ReviewStatus.CLEARED, now);

        List<Long> baseline = activityLogRepository.findRecentDurationsForUserAndCategory(
                1L, Category.STUDY, BASELINE, PageRequest.of(0, 100));

        assertEquals(List.of(60L), baseline);
    }

    @Test
    void findByReviewStatusOrderByCreatedAtDesc_returnsOnlyFlaggedNewestFirst() {
        Activity study = activityRepository.save(Activity.builder().name("Study").category(Category.STUDY).xpMultiplier(1.0).active(true).build());
        LocalDateTime now = LocalDateTime.now();

        log(1L, study, 60L, ReviewStatus.CLEARED, now);
        var older = log(1L, study, 900L, ReviewStatus.FLAGGED, now.minusDays(1));
        var newer = log(1L, study, 950L, ReviewStatus.FLAGGED, now);

        List<ActivityLog> flagged = activityLogRepository.findByReviewStatusOrderByCreatedAtDesc(
                ReviewStatus.FLAGGED, PageRequest.of(0, 100));

        assertEquals(List.of(newer.getId(), older.getId()),
                flagged.stream().map(ActivityLog::getId).toList());
    }
}
```

### 12e. `controller/ActivityLogReviewControllerTest.java` (new) — matches the `@WebMvcTest` idiom (`ActivityControllerTest`), not the plain-Mockito one (`ActivityLogControllerTest`)

```java
package com.tracker.activity.controller;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.dto.FlaggedLogDto;
import com.tracker.activity.service.ActivityLogReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityLogReviewController.class)
class ActivityLogReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityLogReviewService activityLogReviewService;

    @Test
    void testGetFlaggedLogs() throws Exception {
        var dto = new FlaggedLogDto(1L, 2L, "Study", Category.STUDY, 600L, 900.0, LocalDateTime.now(), ReviewStatus.FLAGGED);
        when(activityLogReviewService.getFlaggedLogs()).thenReturn(ResponseEntity.ok(List.of(dto)));

        mockMvc.perform(get("/activitylog/review/flagged").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewStatus").value("FLAGGED"));
    }

    @Test
    void testApprove() throws Exception {
        var dto = new FlaggedLogDto(1L, 2L, "Study", Category.STUDY, 600L, 900.0, LocalDateTime.now(), ReviewStatus.APPROVED);
        when(activityLogReviewService.approve(1L)).thenReturn(ResponseEntity.ok(dto));

        mockMvc.perform(post("/activitylog/review/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        verify(activityLogReviewService).approve(1L);
    }

    @Test
    void testReject() throws Exception {
        var dto = new FlaggedLogDto(1L, 2L, "Study", Category.STUDY, 600L, 900.0, LocalDateTime.now(), ReviewStatus.REJECTED);
        when(activityLogReviewService.reject(1L)).thenReturn(ResponseEntity.ok(dto));

        mockMvc.perform(post("/activitylog/review/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));

        verify(activityLogReviewService).reject(1L);
    }
}
```

### 12f. `dto/ActivityLogRequestValidationTest.java` (new) — proves `endTime` is now bounded (no existing bean-validation test precedent in this repo; self-contained with `jakarta.validation`, already on the classpath via `spring-boot-starter-validation`)

```java
package com.tracker.activity.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityLogRequest Validation Tests")
class ActivityLogRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("a future endTime is now rejected (closes Gap 1 of issue #67)")
    void futureEndTime_isRejected() {
        LocalDateTime now = LocalDateTime.now();
        var request = new ActivityLogRequest("Study", now.minusMinutes(30), now.plusYears(10), "notes", null);

        var violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("endTime")));
    }

    @Test
    @DisplayName("a past-or-present startTime and endTime pass validation")
    void pastOrPresentRange_isValid() {
        LocalDateTime now = LocalDateTime.now();
        var request = new ActivityLogRequest("Study", now.minusMinutes(30), now, "notes", null);

        assertTrue(validator.validate(request).isEmpty());
    }
}
```

## Verification

1. `mvn -pl activity-service -am clean verify` — all green. `DurationOutlierDetectorTest` matters most;
   `ActivityLogRepositoryTest` proves the JPQL translates on H2.
2. **The exploit is closed.** `docker compose up -d`, then `POST /api/activitylog` with
   `startTime = now, endTime = now + 10 years` → **400**, not a multi-million-XP award. Run this first and
   last — it's the regression that motivates the whole change.
3. **Postgres check — the gap H2 cannot close.** `ddl-auto: validate` means the `V4` migration and the
   entity must agree exactly; tests build their schema from entities via `create-drop` and would pass even
   if the migration were wrong. Boot activity-service against real Postgres and confirm it starts.
4. **Quarantine works end to end.** Log ~10 normal 60-minute sessions in one category, then one 900-minute
   session. Expect `reviewStatus: FLAGGED` on the response, **no** `outbox_event` row for that logId, and
   `GET /api/level/user/{id}` totals unchanged after >2s (proving the relay didn't pick it up).
5. **Approval awards exactly once.** `POST /api/activitylog/review/{id}/approve` → within ~2s the XP
   appears. Call approve a second time → refused (409), and
   `SELECT count(*) FROM outbox_event WHERE idempotency_key = '<logId>'` is still 1.
6. **Rejection never awards.** Reject a flagged log, wait >2s, confirm totals unchanged and no outbox row.
7. **Baseline is not poisonable.** With a flagged log outstanding, submit another large session; it must
   still be flagged — i.e. the pending log did not shift the median.
8. **Admin gate.** The three review endpoints return 403 with a `ROLE_USER` token and 200 with `ROLE_ADMIN`,
   through `:8080`; 401 with no token.
9. **Kill switch.** `SESSION_INTEGRITY_OUTLIER_ENABLED=false` → nothing is flagged, the hard cap still
   rejects.

## Out of scope — follow-ups worth their own issues

1. **`POST /level` is an unguarded second door — filed as issue #74, `priority: high`.** It bypasses
   activity-service, the outbox, and everything in this plan. Should land before or alongside #67.
2. **`processed_event` has no Flyway migration** despite `ddl-auto: validate` in gamification-service —
   pre-existing schema drift, unrelated to this work.
3. Excluding `FLAGGED`/`REJECTED` logs from user-facing analytics surfaces (#17 / PR #59, and #65).
4. A backfill scan that scores already-persisted historical logs; this plan only evaluates new writes.
5. Trimmed mean or curated per-category baselines, to harden the cold-start global fallback (Step 6).
6. Persisting the detector's verdict (`modifiedZScore`, `median`, `basis`) alongside the log, so the review
   queue can show *why* something was flagged instead of just the raw numbers. Left out of the MVP `FlaggedLogDto`
   deliberately — it needs either extra columns or a recompute-on-read, and isn't required to ship the
   quarantine mechanism itself.
