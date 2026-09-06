package com.tracker.activity.controller;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.BestTimeOfDayResponse;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.HourOfDayXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.exception.OwnershipViolationException;
import com.tracker.activity.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@DisplayName("Analytics Controller Tests")
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/category-summary returns category summaries")
    void testGetCategorySummary() throws Exception {
        Long userId = 1L;
        CategorySummaryResponse summary = new CategorySummaryResponse(Category.STUDY, 120L, 180.0, 2L);

        when(analyticsService.getCategorySummary(userId, userId))
                .thenReturn(ResponseEntity.ok(List.of(summary)));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/category-summary", userId)
                        .header("userId", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("STUDY"))
                .andExpect(jsonPath("$[0].totalDurationMinutes").value(120))
                .andExpect(jsonPath("$[0].totalXpEarned").value(180.0))
                .andExpect(jsonPath("$[0].totalSessions").value(2));
    }

    // #88: the caller's identity (header) is distinct from the path subject here on purpose --
    // this is the exact shape #76-80 left open, and the service's ownership guard must render
    // as a real 403 through the full @RestControllerAdvice chain, not just a mocked return value.
    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/category-summary is 403 for another user's data (#88)")
    void testGetCategorySummary_forbiddenForOtherUser() throws Exception {
        Long callerUserId = 1L;
        Long otherUsersId = 2L;

        when(analyticsService.getCategorySummary(callerUserId, otherUsersId))
                .thenThrow(new OwnershipViolationException("Not permitted to access another user's data"));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/category-summary", otherUsersId)
                        .header("userId", callerUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/xp-over-time returns daily XP timeline")
    void testGetXpOverTime() throws Exception {
        Long userId = 1L;
        DailyXpResponse dailyResponse = new DailyXpResponse(LocalDate.now(), 90.0, 60L);

        when(analyticsService.getXpOverTime(userId, userId, 7))
                .thenReturn(ResponseEntity.ok(List.of(dailyResponse)));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/xp-over-time", userId)
                        .header("userId", userId)
                        .param("days", "7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalXpEarned").value(90.0))
                .andExpect(jsonPath("$[0].totalDurationMinutes").value(60));
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/xp-over-time is 403 for another user's data (#88)")
    void testGetXpOverTime_forbiddenForOtherUser() throws Exception {
        Long callerUserId = 1L;
        Long otherUsersId = 2L;

        when(analyticsService.getXpOverTime(callerUserId, otherUsersId, 7))
                .thenThrow(new OwnershipViolationException("Not permitted to access another user's data"));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/xp-over-time", otherUsersId)
                        .header("userId", callerUserId)
                        .param("days", "7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/weekly-report returns weekly report")
    void testGetWeeklyReport() throws Exception {
        Long userId = 1L;
        DailyXpResponse dailyResponse = new DailyXpResponse(LocalDate.now(), 90.0, 60L);
        WeeklyReportResponse report = new WeeklyReportResponse(
                180.0, 90.0, 100.0, 120L, Category.STUDY, List.of(dailyResponse)
        );

        when(analyticsService.getWeeklyReport(userId, userId))
                .thenReturn(ResponseEntity.ok(report));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/weekly-report", userId)
                        .header("userId", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekXp").value(180.0))
                .andExpect(jsonPath("$.previousWeekXp").value(90.0))
                .andExpect(jsonPath("$.percentageChange").value(100.0))
                .andExpect(jsonPath("$.totalActiveMinutes").value(120))
                .andExpect(jsonPath("$.topCategory").value("STUDY"));
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/weekly-report is 403 for another user's data (#88)")
    void testGetWeeklyReport_forbiddenForOtherUser() throws Exception {
        Long callerUserId = 1L;
        Long otherUsersId = 2L;

        when(analyticsService.getWeeklyReport(callerUserId, otherUsersId))
                .thenThrow(new OwnershipViolationException("Not permitted to access another user's data"));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/weekly-report", otherUsersId)
                        .header("userId", callerUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/best-time-of-day returns the hourly breakdown and bests (#72)")
    void testGetBestTimeOfDay() throws Exception {
        Long userId = 1L;
        List<HourOfDayXpResponse> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new HourOfDayXpResponse(hour, hour == 9 ? 60L : 0L, hour == 9 ? 200.0 : 0.0, hour == 9 ? 1L : 0L));
        }
        BestTimeOfDayResponse response = new BestTimeOfDayResponse(hourly, 9, Category.STUDY, 9);

        when(analyticsService.getBestTimeOfDay(userId, userId))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/best-time-of-day", userId)
                        .header("userId", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hourlyBreakdown.length()").value(24))
                .andExpect(jsonPath("$.hourlyBreakdown[9].totalXpEarned").value(200.0))
                .andExpect(jsonPath("$.bestHour").value(9))
                .andExpect(jsonPath("$.bestCategory").value("STUDY"))
                .andExpect(jsonPath("$.bestCategoryHour").value(9));
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/best-time-of-day is 403 for another user's data (#88)")
    void testGetBestTimeOfDay_forbiddenForOtherUser() throws Exception {
        Long callerUserId = 1L;
        Long otherUsersId = 2L;

        when(analyticsService.getBestTimeOfDay(callerUserId, otherUsersId))
                .thenThrow(new OwnershipViolationException("Not permitted to access another user's data"));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/best-time-of-day", otherUsersId)
                        .header("userId", callerUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
