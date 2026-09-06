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

        // Study, logged at 9am, is the biggest single contributor -- should win bestHour,
        // bestCategory, and (trivially, since it's Study's only session) bestCategoryHour.
        ActivityLog studyAt9am = ActivityLog.builder()
                .id(1L).userId(userId).activity(studyActivity)
                .startTime(LocalDateTime.of(2026, 1, 5, 9, 0))
                .durationMinutes(60L).xpEarned(200.0)
                .build();

        // Gaming, split across two hours, sums to less than Study's single session.
        ActivityLog gamingAt20pm = ActivityLog.builder()
                .id(2L).userId(userId).activity(gamingActivity)
                .startTime(LocalDateTime.of(2026, 1, 6, 20, 30))
                .durationMinutes(30L).xpEarned(24.0)
                .build();
        ActivityLog gamingAt21pm = ActivityLog.builder()
                .id(3L).userId(userId).activity(gamingActivity)
                .startTime(LocalDateTime.of(2026, 1, 7, 21, 0))
                .durationMinutes(30L).xpEarned(24.0)
                .build();

        when(activityLogRepository.findByUserId(userId))
                .thenReturn(List.of(studyAt9am, gamingAt20pm, gamingAt21pm));

        ResponseEntity<BestTimeOfDayResponse> response = analyticsService.getBestTimeOfDay(userId, userId);

        assertNotNull(response.getBody());
        BestTimeOfDayResponse body = response.getBody();

        // Zero-filled: exactly 24 buckets, one per hour, regardless of how many have logs.
        assertEquals(24, body.hourlyBreakdown().size());
        assertEquals(9, body.hourlyBreakdown().get(9).hour());
        assertEquals(200.0, body.hourlyBreakdown().get(9).totalXpEarned());
        assertEquals(60L, body.hourlyBreakdown().get(9).totalDurationMinutes());
        assertEquals(1L, body.hourlyBreakdown().get(9).totalSessions());
        assertEquals(24.0, body.hourlyBreakdown().get(20).totalXpEarned());
        assertEquals(24.0, body.hourlyBreakdown().get(21).totalXpEarned());
        // An hour with no logs is present (zero-filled), not absent.
        assertEquals(0.0, body.hourlyBreakdown().get(0).totalXpEarned());
        assertEquals(0L, body.hourlyBreakdown().get(0).totalSessions());

        assertEquals(9, body.bestHour());
        assertEquals(Category.STUDY, body.bestCategory());
        assertEquals(9, body.bestCategoryHour());
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
