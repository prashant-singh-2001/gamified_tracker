package com.tracker.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.dto.ActivityLogResponse;
import com.tracker.activity.dto.ReviewQueueItemResponse;
import com.tracker.activity.exception.ActivityNotFoundException;
import com.tracker.activity.exception.ReviewStateConflictException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityLogReviewService Tests (issue #67)")
class ActivityLogReviewServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DurationOutlierEvaluationService durationOutlierEvaluationService;

    private ActivityLogReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ActivityLogReviewServiceImpl(
                activityLogRepository, outboxEventRepository, objectMapper, durationOutlierEvaluationService);
    }

    private ActivityLog flaggedLog(Long id) {
        Activity activity = Activity.builder().id(10L).name("Study").category(Category.STUDY)
                .xpMultiplier(1.5).active(true).build();
        return ActivityLog.builder()
                .id(id)
                .userId(1L)
                .activity(activity)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now())
                .durationMinutes(900L)
                .xpEarned(1350.0)
                .notes("suspicious")
                .createdAt(LocalDateTime.now())
                .reviewStatus(ReviewStatus.FLAGGED)
                .build();
    }

    @Test
    @DisplayName("approve flips FLAGGED -> APPROVED and writes exactly one outbox row keyed by logId")
    void approve_flipsStatusAndWritesOutboxRow() {
        ActivityLog log = flaggedLog(500L);
        when(activityLogRepository.findById(500L)).thenReturn(Optional.of(log));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<ActivityLogResponse> response = reviewService.approve(500L);

        assertEquals(ReviewStatus.APPROVED, response.getBody().reviewStatus());

        ArgumentCaptor<ActivityLog> savedCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(savedCaptor.capture());
        assertEquals(ReviewStatus.APPROVED, savedCaptor.getValue().getReviewStatus());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
        assertEquals("500", outboxCaptor.getValue().getIdempotencyKey());
        assertEquals(500L, outboxCaptor.getValue().getAggregateId());
        assertNull(outboxCaptor.getValue().getPublishedAt());
    }

    @Test
    @DisplayName("reject flips FLAGGED -> REJECTED and writes NO outbox row")
    void reject_flipsStatusAndWritesNoOutboxRow() {
        ActivityLog log = flaggedLog(501L);
        when(activityLogRepository.findById(501L)).thenReturn(Optional.of(log));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<ActivityLogResponse> response = reviewService.reject(501L);

        assertEquals(ReviewStatus.REJECTED, response.getBody().reviewStatus());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("approve on an already-APPROVED log is refused (prevents a second outbox row / double award)")
    void approve_onNonFlaggedLog_isRefused() {
        ActivityLog alreadyApproved = flaggedLog(502L);
        alreadyApproved.setReviewStatus(ReviewStatus.APPROVED);
        when(activityLogRepository.findById(502L)).thenReturn(Optional.of(alreadyApproved));

        assertThrows(ReviewStateConflictException.class, () -> reviewService.approve(502L));

        verify(activityLogRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @DisplayName("reject on a CLEARED log is refused")
    void reject_onNonFlaggedLog_isRefused() {
        ActivityLog cleared = flaggedLog(503L);
        cleared.setReviewStatus(ReviewStatus.CLEARED);
        when(activityLogRepository.findById(503L)).thenReturn(Optional.of(cleared));

        assertThrows(ReviewStateConflictException.class, () -> reviewService.reject(503L));

        verify(activityLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve/reject on a missing log throws ActivityNotFoundException")
    void approve_missingLog_throwsNotFound() {
        when(activityLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> reviewService.approve(999L));
    }

    @Test
    @DisplayName("getFlaggedLogs returns the queue with each log's recomputed verdict")
    void getFlaggedLogs_returnsQueueWithVerdicts() {
        ActivityLog log = flaggedLog(504L);
        when(activityLogRepository.findByReviewStatusOrderByCreatedAtDesc(eq(ReviewStatus.FLAGGED), any(Pageable.class)))
                .thenReturn(List.of(log));
        when(durationOutlierEvaluationService.evaluate(1L, Category.STUDY, 900L))
                .thenReturn(new DurationOutlierDetector.Verdict(true, 12.3, 45.0, 20, DurationOutlierDetector.Basis.MODIFIED_Z_SCORE));

        ResponseEntity<List<ReviewQueueItemResponse>> response = reviewService.getFlaggedLogs();

        assertEquals(1, response.getBody().size());
        ReviewQueueItemResponse item = response.getBody().get(0);
        assertEquals(504L, item.activityLog().id());
        assertEquals(12.3, item.modifiedZScore(), 1e-9);
        assertEquals(DurationOutlierDetector.Basis.MODIFIED_Z_SCORE, item.basis());
    }
}
