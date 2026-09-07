package com.tracker.activity.service;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.BestTimeOfDayResponse;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.exception.OwnershipViolationException;
import com.tracker.activity.repository.ActivityLogRepository;
import com.tracker.activity.service.impl.AnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Analytics Service Tests")
public class AnalyticsServiceImplTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    private Activity studyActivity;
    private Activity gamingActivity;

    @BeforeEach
    void setUp() {
        studyActivity = Activity.builder()
                .id(1L)
                .name("Studying")
                .category(Category.STUDY)
                .xpMultiplier(1.5)
                .active(true)
                .build();

        gamingActivity = Activity.builder()
                .id(2L)
                .name("Gaming")
                .category(Category.GAMING)
                .xpMultiplier(0.8)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Test getCategorySummary aggregates duration, xp and session count correctly")
    void testGetCategorySummary() {
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        ActivityLog log1 = ActivityLog.builder()
                .id(101L)
                .userId(userId)
                .activity(studyActivity)
                .startTime(now.minusHours(2))
                .endTime(now.minusHours(1))
                .durationMinutes(60L)
                .xpEarned(90.0)
                .build();

        ActivityLog log2 = ActivityLog.builder()
                .id(102L)
                .userId(userId)
                .activity(studyActivity)
                .startTime(now.minusDays(1))
                .endTime(now.minusDays(1).plusMinutes(30))
                .durationMinutes(30L)
                .xpEarned(45.0)
                .build();

        ActivityLog log3 = ActivityLog.builder()
                .id(103L)
                .userId(userId)
                .activity(gamingActivity)
                .startTime(now.minusDays(2))
                .endTime(now.minusDays(2).plusMinutes(45))
                .durationMinutes(45L)
                .xpEarned(36.0)
                .build();

        when(activityLogRepository.findByUserId(userId)).thenReturn(List.of(log1, log2, log3));

        ResponseEntity<List<CategorySummaryResponse>> response = analyticsService.getCategorySummary(userId, userId);

        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        CategorySummaryResponse studySummary = response.getBody().stream()
                .filter(s -> s.category() == Category.STUDY)
                .findFirst()
                .orElse(null);

        assertNotNull(studySummary);
        assertEquals(90L, studySummary.totalDurationMinutes());
        assertEquals(135.0, studySummary.totalXpEarned());
        assertEquals(2L, studySummary.totalSessions());
    }

    @Test
    @DisplayName("Test getXpOverTime builds daily timeline for specified days")
    void testGetXpOverTime() {
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        ActivityLog logToday = ActivityLog.builder()
                .id(201L)
                .userId(userId)
                .activity(studyActivity)
                .startTime(now)
                .durationMinutes(60L)
                .xpEarned(90.0)
                .build();

        when(activityLogRepository.findByUserIdAndStartTimeBetween(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(logToday));

        ResponseEntity<List<DailyXpResponse>> response = analyticsService.getXpOverTime(userId, userId, 7);

        assertNotNull(response.getBody());
        assertEquals(7, response.getBody().size());

        DailyXpResponse todayResponse = response.getBody().stream()
                .filter(r -> r.date().equals(LocalDate.now()))
                .findFirst()
                .orElse(null);

        assertNotNull(todayResponse);
        assertEquals(90.0, todayResponse.totalXpEarned());
        assertEquals(60L, todayResponse.totalDurationMinutes());
    }

    @Test
    @DisplayName("Test getWeeklyReport calculates percentage change and top category correctly")
    void testGetWeeklyReport() {
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        ActivityLog currentLog = ActivityLog.builder()
                .id(301L)
                .userId(userId)
                .activity(studyActivity)
                .startTime(now.minusDays(1))
                .durationMinutes(120L)
                .xpEarned(180.0)
                .build();

        ActivityLog prevLog = ActivityLog.builder()
                .id(302L)
                .userId(userId)
                .activity(gamingActivity)
                .startTime(now.minusDays(10))
                .durationMinutes(60L)
                .xpEarned(90.0)
                .build();

        when(activityLogRepository.findByUserIdAndStartTimeBetween(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentLog, prevLog));

        ResponseEntity<WeeklyReportResponse> response = analyticsService.getWeeklyReport(userId, userId);

        assertNotNull(response.getBody());
        WeeklyReportResponse report = response.getBody();

        assertEquals(180.0, report.currentWeekXp());
        assertEquals(90.0, report.previousWeekXp());
        assertEquals(100.0, report.percentageChange());
        assertEquals(120L, report.totalActiveMinutes());
        assertEquals(Category.STUDY, report.topCategory());
        assertEquals(7, report.dailyBreakdown().size());
    }

    @Test
    @DisplayName("Test getWeeklyReport with zero previous week XP returns 100 percent increase")
    void testGetWeeklyReportZeroPreviousWeek() {
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        ActivityLog currentLog = ActivityLog.builder()
                .id(401L)
                .userId(userId)
                .activity(studyActivity)
                .startTime(now.minusDays(2))
                .durationMinutes(60L)
                .xpEarned(100.0)
                .build();

        when(activityLogRepository.findByUserIdAndStartTimeBetween(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentLog));

        ResponseEntity<WeeklyReportResponse> response = analyticsService.getWeeklyReport(userId, userId);

        assertNotNull(response.getBody());
        assertEquals(100.0, response.getBody().percentageChange());
    }

    // #88: all four analytics reads are keyed by a userId path variable with no other object to
    // compare against, so a caller/subject mismatch is always a 403 -- pinned once per method,
    // and each one must short-circuit before touching the repository.
    @Test
    @DisplayName("getCategorySummary rejects a caller asking for another user's analytics (#88)")
    void testGetCategorySummary_rejectsOtherUser() {
        assertThrows(OwnershipViolationException.class,
                () -> analyticsService.getCategorySummary(1L, 2L));
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    @DisplayName("getXpOverTime rejects a caller asking for another user's analytics (#88)")
    void testGetXpOverTime_rejectsOtherUser() {
        assertThrows(OwnershipViolationException.class,
                () -> analyticsService.getXpOverTime(1L, 2L, 7));
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    @DisplayName("getWeeklyReport rejects a caller asking for another user's analytics (#88)")
    void testGetWeeklyReport_rejectsOtherUser() {
        assertThrows(OwnershipViolationException.class,
                () -> analyticsService.getWeeklyReport(1L, 2L));
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    @DisplayName("getBestTimeOfDay rejects a caller asking for another user's analytics (#88)")
    void testGetBestTimeOfDay_rejectsOtherUser() {
        assertThrows(OwnershipViolationException.class,
                () -> analyticsService.getBestTimeOfDay(1L, 2L));
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    @DisplayName("getBestTimeOfDay (#72) buckets XP by hour_of_day and picks the peak hour, category, and category-peak hour")
    void testGetBestTimeOfDay_buildsHourlyBreakdownAndPicksBests() {
        Long userId = 1L;

        // Study, logged at 9am, is the single biggest hour -- wins bestHour outright.
        ActivityLog studyAt9am = ActivityLog.builder()
                .id(1L).userId(userId).activity(studyActivity)
                .startTime(LocalDateTime.of(2026, 1, 5, 9, 0))
                .durationMinutes(60L).xpEarned(200.0)
                .build();

        // Gaming, split across two hours (130 + 120 = 250), outscores Study's single 200-XP
        // hour on CATEGORY total even though neither individual Gaming hour beats hour 9 --
        // this is what makes bestHour (9), bestCategory (GAMING), and bestCategoryHour (21)
        // three genuinely different values. A fixture where all three coincide (as an earlier
        // version of this test had) can't catch a bug that drops the category filter from the
        // bestCategoryHour derivation -- see testGetBestTimeOfDay_bestCategoryHourRequiresCategoryFilter.
        ActivityLog gamingAt21pm = ActivityLog.builder()
                .id(2L).userId(userId).activity(gamingActivity)
                .startTime(LocalDateTime.of(2026, 1, 6, 21, 0))
                .durationMinutes(30L).xpEarned(130.0)
                .build();
        ActivityLog gamingAt22pm = ActivityLog.builder()
                .id(3L).userId(userId).activity(gamingActivity)
                .startTime(LocalDateTime.of(2026, 1, 7, 22, 0))
                .durationMinutes(30L).xpEarned(120.0)
                .build();

        when(activityLogRepository.findByUserId(userId))
                .thenReturn(List.of(studyAt9am, gamingAt21pm, gamingAt22pm));

        ResponseEntity<BestTimeOfDayResponse> response = analyticsService.getBestTimeOfDay(userId, userId);

        assertNotNull(response.getBody());
        BestTimeOfDayResponse body = response.getBody();

        // Zero-filled: exactly 24 buckets, one per hour, regardless of how many have logs.
        assertEquals(24, body.hourlyBreakdown().size());
        assertEquals(9, body.hourlyBreakdown().get(9).hour());
        assertEquals(200.0, body.hourlyBreakdown().get(9).totalXpEarned());
        assertEquals(60L, body.hourlyBreakdown().get(9).totalDurationMinutes());
        assertEquals(1L, body.hourlyBreakdown().get(9).totalSessions());
        assertEquals(130.0, body.hourlyBreakdown().get(21).totalXpEarned());
        assertEquals(120.0, body.hourlyBreakdown().get(22).totalXpEarned());
        // An hour with no logs is present (zero-filled), not absent.
        assertEquals(0.0, body.hourlyBreakdown().get(0).totalXpEarned());
        assertEquals(0L, body.hourlyBreakdown().get(0).totalSessions());

        // Three deliberately different values: 200 (hour 9) beats either single Gaming hour, but
        // Gaming's 130+120=250 category total beats Study's 200, and within Gaming, 21 (130) beats
        // 22 (120).
        assertEquals(9, body.bestHour());
        assertEquals(Category.GAMING, body.bestCategory());
        assertEquals(21, body.bestCategoryHour());
    }

    // #103 review: the original fixture had bestCategoryHour == bestHour == 9 for every category,
    // so deleting the "&& category == bestCategory" filter from the derivation (collapsing it into
    // a duplicate of bestHour, and destroying the "hour Y WITHIN category X" semantics the feature
    // exists for) still passed. This fixture only passes if that filter is actually applied: Study
    // has more total XP than Gaming, so bestCategory=STUDY, but Study's own peak hour (9, at 50 XP)
    // is not the same as Gaming's peak hour (14, at 90 XP) -- if bestCategoryHour were derived from
    // the unfiltered hourlyBreakdown instead of Study-only logs, it would wrongly report 14.
    @Test
    @DisplayName("getBestTimeOfDay (#72) bestCategoryHour is scoped to bestCategory's own logs, not the overall peak hour")
    void testGetBestTimeOfDay_bestCategoryHourRequiresCategoryFilter() {
        Long userId = 1L;

        ActivityLog studyAt9am = ActivityLog.builder()
                .id(1L).userId(userId).activity(studyActivity)
                .startTime(LocalDateTime.of(2026, 1, 5, 9, 0))
                .durationMinutes(30L).xpEarned(50.0)
                .build();
        ActivityLog studyAt10am = ActivityLog.builder()
                .id(2L).userId(userId).activity(studyActivity)
                .startTime(LocalDateTime.of(2026, 1, 6, 10, 0))
                .durationMinutes(30L).xpEarned(45.0)
                .build();
        // Gaming's single session outscores either individual Study hour, but not Study's total.
        ActivityLog gamingAt2pm = ActivityLog.builder()
                .id(3L).userId(userId).activity(gamingActivity)
                .startTime(LocalDateTime.of(2026, 1, 7, 14, 0))
                .durationMinutes(30L).xpEarned(90.0)
                .build();

        when(activityLogRepository.findByUserId(userId))
                .thenReturn(List.of(studyAt9am, studyAt10am, gamingAt2pm));

        BestTimeOfDayResponse body = analyticsService.getBestTimeOfDay(userId, userId).getBody();

        assertNotNull(body);
        assertEquals(14, body.bestHour()); // Gaming's single 90-XP hour beats either Study hour alone
        assertEquals(Category.STUDY, body.bestCategory()); // but Study's 50+45=95 beats Gaming's 90
        assertEquals(9, body.bestCategoryHour()); // Study's OWN peak hour (50 > 45), not hour 14
    }

    // #103 review: bestHour is filtered against > 0.0, but bestCategory/bestCategoryHour
    // previously were not -- a user whose logs all earn 0.0 XP got bestHour=null alongside a
    // non-null bestCategory/bestCategoryHour, a self-contradictory payload. All three must go
    // null together.
    @Test
    @DisplayName("getBestTimeOfDay (#72) returns null for all three bests when every log earns 0.0 XP")
    void testGetBestTimeOfDay_allZeroXp_returnsNullBests() {
        Long userId = 1L;
        ActivityLog zeroXpLog = ActivityLog.builder()
                .id(1L).userId(userId).activity(studyActivity)
                .startTime(LocalDateTime.of(2026, 1, 5, 9, 0))
                .durationMinutes(0L).xpEarned(0.0)
                .build();

        when(activityLogRepository.findByUserId(userId)).thenReturn(List.of(zeroXpLog));

        BestTimeOfDayResponse body = analyticsService.getBestTimeOfDay(userId, userId).getBody();

        assertNotNull(body);
        assertEquals(24, body.hourlyBreakdown().size());
        assertNull(body.bestHour());
        assertNull(body.bestCategory());
        assertNull(body.bestCategoryHour());
    }

    @Test
    @DisplayName("getBestTimeOfDay (#72) returns a zero-filled breakdown and null bests when the user has no logs")
    void testGetBestTimeOfDay_noLogs() {
        Long userId = 42L;
        when(activityLogRepository.findByUserId(userId)).thenReturn(List.of());

        ResponseEntity<BestTimeOfDayResponse> response = analyticsService.getBestTimeOfDay(userId, userId);

        assertNotNull(response.getBody());
        BestTimeOfDayResponse body = response.getBody();
        assertEquals(24, body.hourlyBreakdown().size());
        assertNull(body.bestHour());
        assertNull(body.bestCategory());
        assertNull(body.bestCategoryHour());
    }
}
