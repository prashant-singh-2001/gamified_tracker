package com.tracker.activity.service;

import com.tracker.activity.config.InsightsProperties;
import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.DigestFacts;
import com.tracker.activity.domain.WeeklyDigestNarrator;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.NarrativeStatus;
import com.tracker.activity.dto.WeeklyInsightsResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.impl.InsightsServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Insights Service Tests (issue #65)")
class InsightsServiceImplTest {

    private static final WeeklyReportResponse DEFAULT_TOTALS =
            new WeeklyReportResponse(100.0, 80.0, 25.0, 200L, Category.STUDY, List.of());

    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private AnalyticsService analyticsService;

    private StubNarrator narrator;
    private InsightsProperties insightsProperties;
    private MeterRegistry meterRegistry;
    private InsightsServiceImpl insightsService;

    private Activity studyActivity;
    private Activity healthActivity;

    @BeforeEach
    void setUp() {
        studyActivity = Activity.builder().id(1L).name("Study").category(Category.STUDY).xpMultiplier(1.5).active(true).build();
        healthActivity = Activity.builder().id(2L).name("Run").category(Category.HEALTH).xpMultiplier(1.3).active(true).build();

        narrator = new StubNarrator();
        insightsProperties = new InsightsProperties(true, 20, 280, 1200);
        meterRegistry = new SimpleMeterRegistry();
        insightsService = new InsightsServiceImpl(
                activityLogRepository, analyticsService, narrator, insightsProperties, meterRegistry);

        // Neutral defaults for tests that don't care about the exact totals/rows -- lenient()
        // because the delegation-specific tests below stub these explicitly.
        lenient().when(analyticsService.getWeeklyReport(anyLong())).thenReturn(ResponseEntity.ok(DEFAULT_TOTALS));
        lenient().when(activityLogRepository.findByUserIdAndStartTimeBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("per-category totals for the week are computed independently of the delegated headline totals")
    void getWeeklyInsights_aggregatesPerCategory() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLog studyLog1 = ActivityLog.builder().userId(1L).activity(studyActivity)
                .startTime(now.minusDays(1)).durationMinutes(60L).xpEarned(90.0).build();
        ActivityLog studyLog2 = ActivityLog.builder().userId(1L).activity(studyActivity)
                .startTime(now.minusDays(2)).durationMinutes(30L).xpEarned(45.0).build();
        ActivityLog healthLog = ActivityLog.builder().userId(1L).activity(healthActivity)
                .startTime(now.minusDays(3)).durationMinutes(20L).xpEarned(26.0).build();
        when(activityLogRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(studyLog1, studyLog2, healthLog));

        WeeklyInsightsResponse response = insightsService.getWeeklyInsights(1L).getBody();

        assertNotNull(response);
        assertEquals(2, response.categories().size());
        CategorySummaryResponse study = response.categories().stream()
                .filter(c -> c.category() == Category.STUDY).findFirst().orElseThrow();
        assertEquals(90L, study.totalDurationMinutes());
        assertEquals(135.0, study.totalXpEarned());
        assertEquals(2L, study.totalSessions());
    }

    @Test
    @DisplayName("notes from the week's rows reach the narrator newest-first, blanks excluded")
    void getWeeklyInsights_collectsNotesNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        ActivityLog older = ActivityLog.builder().userId(1L).activity(studyActivity)
                .startTime(now.minusDays(3)).durationMinutes(10L).xpEarned(15.0).notes("older note").build();
        ActivityLog newer = ActivityLog.builder().userId(1L).activity(studyActivity)
                .startTime(now.minusDays(1)).durationMinutes(10L).xpEarned(15.0).notes("newer note").build();
        ActivityLog blankNoted = ActivityLog.builder().userId(1L).activity(studyActivity)
                .startTime(now.minusDays(2)).durationMinutes(10L).xpEarned(15.0).notes("  ").build();
        when(activityLogRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(older, newer, blankNoted));

        insightsService.getWeeklyInsights(1L);

        assertNotNull(narrator.lastFacts);
        assertEquals(List.of("newer note", "older note"), narrator.lastFacts.noteLines());
    }

    @Test
    @DisplayName("a narrator that throws degrades to numbers-only instead of propagating")
    void getWeeklyInsights_narratorThrows_degradesGracefully() {
        narrator.toThrow = new RuntimeException("model timeout");

        ResponseEntity<WeeklyInsightsResponse> responseEntity =
                assertDoesNotThrow(() -> insightsService.getWeeklyInsights(1L));

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        WeeklyInsightsResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertNull(response.narrative());
        assertEquals(NarrativeStatus.UNAVAILABLE, response.narrativeStatus());
        assertEquals(1, meterRegistry.get("activity.insights.narrative").tag("outcome", "UNAVAILABLE").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("an empty narrator result reports DISABLED when the feature flag itself is off")
    void getWeeklyInsights_emptyResultAndFlagOff_reportsDisabled() {
        InsightsProperties disabledProperties = new InsightsProperties(false, 20, 280, 1200);
        InsightsServiceImpl serviceWithFlagOff = new InsightsServiceImpl(
                activityLogRepository, analyticsService, narrator, disabledProperties, meterRegistry);

        WeeklyInsightsResponse response = serviceWithFlagOff.getWeeklyInsights(1L).getBody();

        assertNotNull(response);
        assertNull(response.narrative());
        assertEquals(NarrativeStatus.DISABLED, response.narrativeStatus());
    }

    @Test
    @DisplayName("an empty narrator result reports UNAVAILABLE when the flag is on but nothing answered")
    void getWeeklyInsights_emptyResultAndFlagOn_reportsUnavailable() {
        // insightsProperties from setUp() already has enabled=true; narrator's default result
        // is Optional.empty().
        WeeklyInsightsResponse response = insightsService.getWeeklyInsights(1L).getBody();

        assertNotNull(response);
        assertNull(response.narrative());
        assertEquals(NarrativeStatus.UNAVAILABLE, response.narrativeStatus());
    }

    @Test
    @DisplayName("a narrative longer than maxNarrativeChars is truncated regardless of which narrator produced it")
    void getWeeklyInsights_overlongNarrative_isTruncated() {
        InsightsProperties tightProperties = new InsightsProperties(true, 20, 280, 10);
        InsightsServiceImpl serviceWithTightLimit = new InsightsServiceImpl(
                activityLogRepository, analyticsService, narrator, tightProperties, meterRegistry);
        narrator.result = Optional.of("A".repeat(50));

        WeeklyInsightsResponse response = serviceWithTightLimit.getWeeklyInsights(1L).getBody();

        assertNotNull(response);
        assertEquals(10, response.narrative().length());
        assertEquals(NarrativeStatus.GENERATED, response.narrativeStatus());
    }

    @Test
    @DisplayName("headline totals are delegated to AnalyticsService, never recomputed")
    void getWeeklyInsights_delegatesHeadlineTotalsToAnalyticsService() {
        WeeklyInsightsResponse response = insightsService.getWeeklyInsights(1L).getBody();

        verify(analyticsService).getWeeklyReport(1L);
        assertNotNull(response);
        assertSame(DEFAULT_TOTALS, response.totals());
    }

    /** Provider-agnostic by design (not a Mockito mock) -- stays valid whichever backend is configured. */
    private static final class StubNarrator implements WeeklyDigestNarrator {
        private Optional<String> result = Optional.empty();
        private RuntimeException toThrow;
        private DigestFacts lastFacts;

        @Override
        public Optional<String> narrate(DigestFacts facts) {
            this.lastFacts = facts;
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }
    }
}
