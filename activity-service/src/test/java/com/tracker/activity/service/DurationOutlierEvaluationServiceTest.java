package com.tracker.activity.service;

import com.tracker.activity.config.SessionIntegrityProperties;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dao.ReviewStatus;
import com.tracker.activity.domain.DurationOutlierDetector;
import com.tracker.activity.repository.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DurationOutlierEvaluationService Tests (issue #67)")
class DurationOutlierEvaluationServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private DurationOutlierDetector durationOutlierDetector;

    // absoluteFlagMinutes=600, minSamples=10, baselineWindow=100 -- matches the documented defaults
    private final SessionIntegrityProperties properties =
            new SessionIntegrityProperties(true, 1440, 3.5, 10, 100, 3.0, 600, 1440);

    private DurationOutlierEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new DurationOutlierEvaluationService(activityLogRepository, durationOutlierDetector, properties);
    }

    @Test
    @DisplayName("a duration over absoluteFlagMinutes short-circuits: flags without touching the database or the detector")
    void overAbsoluteThreshold_shortCircuitsBeforeAnyLookup() {
        var verdict = evaluationService.evaluate(1L, Category.STUDY, 601L);

        assertTrue(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.ABSOLUTE_THRESHOLD, verdict.basis());
        verifyNoInteractions(activityLogRepository);
        verifyNoInteractions(durationOutlierDetector);
    }

    @Test
    @DisplayName("a duration exactly at absoluteFlagMinutes does NOT trip the absolute threshold (strictly greater-than)")
    void atAbsoluteThreshold_doesNotShortCircuit() {
        when(activityLogRepository.findRecentDurationsForUserAndCategory(eq(1L), eq(Category.STUDY), any(), any(Pageable.class)))
                .thenReturn(List.of(60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L));
        when(durationOutlierDetector.evaluate(eq(600L), any()))
                .thenReturn(new DurationOutlierDetector.Verdict(false, 0.0, 60.0, 10, DurationOutlierDetector.Basis.RELATIVE_FALLBACK));

        var verdict = evaluationService.evaluate(1L, Category.STUDY, 600L);

        assertEquals(DurationOutlierDetector.Basis.RELATIVE_FALLBACK, verdict.basis());
        verify(activityLogRepository).findRecentDurationsForUserAndCategory(eq(1L), eq(Category.STUDY), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("below the absolute threshold with a full per-user baseline: uses it directly, never queries the global fallback")
    void belowThreshold_sufficientPerUserBaseline_skipsGlobalFallback() {
        List<Long> baseline = List.of(60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L); // 10 = minSamples
        when(activityLogRepository.findRecentDurationsForUserAndCategory(eq(1L), eq(Category.STUDY), any(), any(Pageable.class)))
                .thenReturn(baseline);
        when(durationOutlierDetector.evaluate(eq(500L), eq(baseline)))
                .thenReturn(new DurationOutlierDetector.Verdict(false, 1.2, 60.0, 10, DurationOutlierDetector.Basis.MODIFIED_Z_SCORE));

        var verdict = evaluationService.evaluate(1L, Category.STUDY, 500L);

        assertEquals(DurationOutlierDetector.Basis.MODIFIED_Z_SCORE, verdict.basis());
        verify(activityLogRepository, never()).findRecentDurationsForCategory(any(), any(), any());
    }

    @Test
    @DisplayName("below the absolute threshold with a thin per-user baseline: falls back to the category-wide cold-start baseline")
    void belowThreshold_thinPerUserBaseline_fallsBackToGlobal() {
        List<Long> thinBaseline = List.of(60L, 60L); // 2 < minSamples(10)
        List<Long> globalBaseline = List.of(45L, 50L, 55L, 60L, 60L, 65L, 70L, 75L, 80L, 85L, 90L);
        when(activityLogRepository.findRecentDurationsForUserAndCategory(eq(1L), eq(Category.STUDY), any(), any(Pageable.class)))
                .thenReturn(thinBaseline);
        when(activityLogRepository.findRecentDurationsForCategory(eq(Category.STUDY), any(), any(Pageable.class)))
                .thenReturn(globalBaseline);
        when(durationOutlierDetector.evaluate(eq(500L), eq(globalBaseline)))
                .thenReturn(new DurationOutlierDetector.Verdict(true, 4.1, 65.0, 11, DurationOutlierDetector.Basis.MODIFIED_Z_SCORE));

        var verdict = evaluationService.evaluate(1L, Category.STUDY, 500L);

        assertTrue(verdict.flagged());
        verify(activityLogRepository).findRecentDurationsForCategory(eq(Category.STUDY), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("the baseline queries are scoped to CLEARED and APPROVED only (poisoning guard)")
    void baselineQueries_areScopedToClearedAndApproved() {
        when(activityLogRepository.findRecentDurationsForUserAndCategory(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(activityLogRepository.findRecentDurationsForCategory(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(durationOutlierDetector.evaluate(anyLong(), any()))
                .thenReturn(new DurationOutlierDetector.Verdict(false, 0.0, 0.0, 0, DurationOutlierDetector.Basis.INSUFFICIENT_SAMPLES));

        evaluationService.evaluate(1L, Category.STUDY, 100L);

        @SuppressWarnings("unchecked")
        Collection<ReviewStatus>[] captured = new Collection[1];
        verify(activityLogRepository).findRecentDurationsForUserAndCategory(any(), any(), argThat(statuses -> {
            captured[0] = (Collection<ReviewStatus>) statuses;
            return true;
        }), any(Pageable.class));
        assertEquals(java.util.Set.of(ReviewStatus.CLEARED, ReviewStatus.APPROVED), java.util.Set.copyOf(captured[0]));
    }
}
