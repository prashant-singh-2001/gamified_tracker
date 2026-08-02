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
import com.tracker.activity.service.DurationOutlierEvaluationService;
import com.tracker.contracts.event.ActivityLoggedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


@AllArgsConstructor
@Service
public class ActivityLogServiceImpl implements ActivityLogService {

    // Session integrity (#67): a withheld (FLAGGED) or denied (REJECTED) log must not consume the
    // user's legitimate daily budget — mirrors DurationOutlierEvaluationService's baseline exclusion.
    private static final Set<ReviewStatus> COUNTED_TOWARD_DAILY_CAP = EnumSet.of(ReviewStatus.CLEARED, ReviewStatus.APPROVED);

    private final ActivityLogRepository activityLogRepository;
    private final ActivityRepository activityRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ActivityStreakRepository activityStreakRepository;
    private final DurationOutlierEvaluationService durationOutlierEvaluationService;
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
        activityLog.setDurationMinutes(
                Duration.between(activityLog.getStartTime(), activityLog.getEndTime()).toMinutes());
        activityLog.setUserId(userId);
        LocalDate activityDate = activityLog.getStartTime().toLocalDate();

        // Session integrity (#67) layer 1: hard cap. A 30-hour session is impossible input, not
        // ambiguous input — same class as the endTime.isAfter(startTime) guard above. Checked
        // before the streak write so a reject doesn't do work the rollback would undo anyway.
        long maxDurationMinutes = sessionIntegrityProperties.maxDurationMinutes();
        if (activityLog.getDurationMinutes() > maxDurationMinutes) {
            meterRegistry.counter("activity.log.rejected.duration",
                    "category", activityLog.getActivity().getCategory().name()).increment();
            throw new ImplausibleSessionException(activityLog.getDurationMinutes(), maxDurationMinutes);
        }

        // Session integrity (#67) layer 1b: per-user-per-day aggregate cap. Closes aggregate
        // farming via many small sessions, which the per-session cap above cannot catch. A prior
        // FLAGGED/REJECTED log does not consume the budget (COUNTED_TOWARD_DAILY_CAP).
        long maxDailyMinutes = sessionIntegrityProperties.maxDailyMinutes();
        Long existingDailyTotal = activityLogRepository.sumDurationForUserOnDay(
                userId, activityDate.atStartOfDay(), activityDate.atTime(LocalTime.MAX), COUNTED_TOWARD_DAILY_CAP);
        long runningDailyTotal = (existingDailyTotal != null ? existingDailyTotal : 0L) + activityLog.getDurationMinutes();
        if (runningDailyTotal > maxDailyMinutes) {
            meterRegistry.counter("activity.log.rejected.dailycap",
                    "category", activityLog.getActivity().getCategory().name()).increment();
            throw new ImplausibleSessionException(activityLog.getDurationMinutes(), runningDailyTotal, maxDailyMinutes);
        }

        // ThreadLocalRandom avoids the pre-existing RandomGenerator.getDefault() "L32X64MixRandom"
        // failure (bug #2) that 500s this endpoint on some JVM/container images.
        var random = ThreadLocalRandom.current();

        ActivityStreak streak = applyStreak(userId, activityLog.getActivity().getId(), activityDate);
        double streakMult = streakMultiplier(streak.getCurrentStreak());

        // Source of truth (#10): per-activity override when set (> 0), else the Category base.
        double multiplier = activityLog.getActivity().effectiveXpMultiplier();
        double bonus = random.nextDouble() < 0.2 ? random.nextDouble(1.1, 1.5) : 1.0;
        activityLog.setXpEarned(activityLog.getDurationMinutes() * multiplier * bonus * streakMult);

        // Session integrity (#67) layer 2: statistical outlier detection. A flagged log is still
        // persisted with the streak advanced and xpEarned frozen on the row — only the outbox
        // write (the point of no return) is withheld, pending maintainer review.
        DurationOutlierDetector.Verdict verdict = evaluateOutlier(activityLog);
        boolean flagged = verdict.flagged();
        activityLog.setReviewStatus(flagged ? ReviewStatus.FLAGGED : ReviewStatus.CLEARED);

        // 1) persist the log FIRST (fixes #4) — the generated id is our logId / idempotency key
        var saved = activityLogRepository.save(activityLog);

        // 2) SAME transaction: write the outbox row (atomic with the log insert) — UNLESS the log
        // was flagged, in which case no outbox row is written at all; approval writes it later.
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
        } else {
            // basis tag distinguishes the absolute threshold (tune it) from the statistical
            // layer (leave it alone) when reading the false-positive rate off this counter.
            meterRegistry.counter("activity.log.flagged",
                    "category", saved.getActivity().getCategory().name(),
                    "basis", verdict.basis().name()).increment();
        }

        boolean bonusApplied = bonus != 1.0;
        // leveledUp is now EVENTUAL (XP applied async by the consumer) -> false at write time
        return ResponseEntity.ok(mapToActivityLogResponse(saved, bonusApplied, bonus, false, streak.getCurrentStreak(), streakMult));
    }

    /**
     * Session integrity (#67) layer 2. Returns the detector's verdict for the candidate duration
     * against the user's (falling back to the category-wide) baseline. Below {@code min-samples}
     * priors, the detector abstains rather than flagging on thin evidence — cold-start users are
     * exactly who should not be flagged. When layer 2 is disabled, returns a non-flagged verdict.
     */
    private DurationOutlierDetector.Verdict evaluateOutlier(ActivityLog activityLog) {
        if (!sessionIntegrityProperties.outlierDetectionEnabled()) {
            return new DurationOutlierDetector.Verdict(false, 0.0, 0.0, 0, DurationOutlierDetector.Basis.INSUFFICIENT_SAMPLES);
        }
        return durationOutlierEvaluationService.evaluate(
                activityLog.getUserId(), activityLog.getActivity().getCategory(), activityLog.getDurationMinutes()
        );
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
