package com.tracker.activity.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.ReviewQueueItemResponse;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.ReviewStateConflictException;
import com.tracker.activity.outbox.OutboxEvent;
import com.tracker.activity.outbox.OutboxEventRepository;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.ActivityLogReviewService;
import com.tracker.activity.service.DurationOutlierEvaluationService;
import com.tracker.contracts.event.ActivityLoggedEvent;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin review queue for logs quarantined by session-integrity layer 2 (issue #67). Approval is
 * the only place outside {@code ActivityLogServiceImpl} that writes an outbox row — it's what
 * lets the existing 2-second relay award the already-computed {@code xpEarned} naturally.
 */
@Service
@AllArgsConstructor
public class ActivityLogReviewServiceImpl implements ActivityLogReviewService {

    private static final int QUEUE_PAGE_SIZE = 100;

    private final ActivityLogRepository activityLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final DurationOutlierEvaluationService durationOutlierEvaluationService;

    @Override
    public ResponseEntity<List<ReviewQueueItemResponse>> getFlaggedLogs() {
        var flagged = activityLogRepository.findByReviewStatusOrderByCreatedAtDesc(
                ReviewStatus.FLAGGED, PageRequest.of(0, QUEUE_PAGE_SIZE));

        var items = flagged.stream().map(log -> {
            DurationOutlierDetector.Verdict verdict = durationOutlierEvaluationService.evaluate(
                    log.getUserId(), log.getActivity().getCategory(), log.getDurationMinutes());
            return new ReviewQueueItemResponse(
                    mapToActivityLogResponse(log),
                    verdict.modifiedZScore(),
                    verdict.median(),
                    verdict.sampleSize(),
                    verdict.basis());
        }).toList();

        return ResponseEntity.ok(items);
    }

    @Override
    @Transactional
    public ResponseEntity<ActivityLogResponse> approve(Long logId) {
        var log = findFlaggedOrThrow(logId);

        log.setReviewStatus(ReviewStatus.APPROVED);
        var saved = activityLogRepository.save(log);

        // Idempotent by the unique idempotency_key (= logId): calling approve twice on a log that
        // somehow reached APPROVED again would violate the constraint rather than double-award.
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

        return ResponseEntity.ok(mapToActivityLogResponse(saved));
    }

    @Override
    @Transactional
    public ResponseEntity<ActivityLogResponse> reject(Long logId) {
        var log = findFlaggedOrThrow(logId);

        log.setReviewStatus(ReviewStatus.REJECTED);
        var saved = activityLogRepository.save(log);

        // No compensation logic needed: rejection is simply never writing the outbox row.
        return ResponseEntity.ok(mapToActivityLogResponse(saved));
    }

    private ActivityLog findFlaggedOrThrow(Long logId) {
        var log = activityLogRepository.findById(logId)
                .orElseThrow(() -> new ActivityNotFoundException("Activity log not found: " + logId));
        if (log.getReviewStatus() != ReviewStatus.FLAGGED) {
            throw new ReviewStateConflictException(logId, log.getReviewStatus());
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

    // Review-queue/approve/reject responses don't have the write-time bonus/streak context —
    // same "historical log" default pattern as ActivityLogServiceImpl#getActivityLogResponseEntity.
    private ActivityLogResponse mapToActivityLogResponse(ActivityLog activityLog) {
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
                false,
                1.0,
                false,
                0,
                1.0,
                activityLog.getReviewStatus(),
                null // Issue #66: no write-time fuzzy-resolution context for a historical log
        );
    }
}
