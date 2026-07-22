package com.tracker.activity.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dto.ActivityLogRequest;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.InvalidTimeRangeException;
import com.tracker.activity.messaging.ActivityLoggedEvent;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.repository.ActivityRepository;
import com.tracker.activity.service.ActivityLogService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


@AllArgsConstructor
@Service
public class ActivityLogServiceImpl implements ActivityLogService {
    private final ActivityLogRepository activityLogRepository;
    private final ActivityRepository activityRepository;
    private final OutboxEventRepository outboxEventRepository;   // was GamificationClient
    private final ObjectMapper objectMapper;


    @Override
    public ResponseEntity<ActivityLogResponse> getActivityLogResponseEntity(Long id) {
        var activityLog = activityLogRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException("Activity log not found: " + id));

        // historical logs don't have bonus/leveled flags stored yet — return defaults
        return ResponseEntity.ok(mapToActivityLogResponse(activityLog, false, 1.0, false));
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

        // ThreadLocalRandom avoids the pre-existing RandomGenerator.getDefault() "L32X64MixRandom"
        // failure (bug #2) that 500s this endpoint on some JVM/container images.
        var random = ThreadLocalRandom.current();
        // Source of truth (#10): per-activity override when set (> 0), else the Category base.
        double multiplier = activityLog.getActivity().effectiveXpMultiplier();
        double bonus = random.nextDouble() < 0.2 ? random.nextDouble(1.1, 1.5) : 1.0;
        activityLog.setXpEarned(activityLog.getDurationMinutes() * multiplier * bonus);

        // 1) persist the log FIRST (fixes #4) — the generated id is our logId / idempotency key
        var saved = activityLogRepository.save(activityLog);

        // 2) SAME transaction: write the outbox row (atomic with the log insert)
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

        boolean bonusApplied = bonus != 1.0;
        // leveledUp is now EVENTUAL (XP applied async by the consumer) -> false at write time
        return ResponseEntity.ok(mapToActivityLogResponse(saved, bonusApplied, bonus, false));
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

        var activityLogResponses = activityLogList.stream().map(a -> mapToActivityLogResponse(a, false, 1.0, false)).toList();

        return ResponseEntity.ok(activityLogResponses);
    }

    private ActivityLog mapToActivityLog(Long userId, ActivityLogRequest activityLogRequest) {
        return ActivityLog.builder()
                .userId(userId)
                .activity(activityRepository.findByName(activityLogRequest.activityName())
                        .orElseThrow(() -> new ActivityNotFoundException("Activity not found: " + activityLogRequest.activityName())))
                .startTime(activityLogRequest.startTime())
                .endTime(activityLogRequest.endTime())
                .notes(activityLogRequest.notes())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActivityLogResponse mapToActivityLogResponse(ActivityLog activityLog, boolean bonusApplied, double bonusMultiplier, boolean leveledUp) {
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
                leveledUp
        );
    }
}
