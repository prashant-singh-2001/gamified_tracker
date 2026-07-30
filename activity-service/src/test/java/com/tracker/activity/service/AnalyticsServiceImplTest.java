package com.tracker.activity.service;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        ResponseEntity<List<CategorySummaryResponse>> response = analyticsService.getCategorySummary(userId);

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

        ResponseEntity<List<DailyXpResponse>> response = analyticsService.getXpOverTime(userId, 7);

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

        ResponseEntity<WeeklyReportResponse> response = analyticsService.getWeeklyReport(userId);

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

        ResponseEntity<WeeklyReportResponse> response = analyticsService.getWeeklyReport(userId);

        assertNotNull(response.getBody());
        assertEquals(100.0, response.getBody().percentageChange());
    }
}
