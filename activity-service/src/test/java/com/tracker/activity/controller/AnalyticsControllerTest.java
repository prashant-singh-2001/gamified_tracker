package com.tracker.activity.controller;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
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

        when(analyticsService.getCategorySummary(userId))
                .thenReturn(ResponseEntity.ok(List.of(summary)));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/category-summary", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("STUDY"))
                .andExpect(jsonPath("$[0].totalDurationMinutes").value(120))
                .andExpect(jsonPath("$[0].totalXpEarned").value(180.0))
                .andExpect(jsonPath("$[0].totalSessions").value(2));
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/xp-over-time returns daily XP timeline")
    void testGetXpOverTime() throws Exception {
        Long userId = 1L;
        DailyXpResponse dailyResponse = new DailyXpResponse(LocalDate.now(), 90.0, 60L);

        when(analyticsService.getXpOverTime(userId, 7))
                .thenReturn(ResponseEntity.ok(List.of(dailyResponse)));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/xp-over-time", userId)
                        .param("days", "7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalXpEarned").value(90.0))
                .andExpect(jsonPath("$[0].totalDurationMinutes").value(60));
    }

    @Test
    @DisplayName("GET /activitylog/analytics/user/{userId}/weekly-report returns weekly report")
    void testGetWeeklyReport() throws Exception {
        Long userId = 1L;
        DailyXpResponse dailyResponse = new DailyXpResponse(LocalDate.now(), 90.0, 60L);
        WeeklyReportResponse report = new WeeklyReportResponse(
                180.0, 90.0, 100.0, 120L, Category.STUDY, List.of(dailyResponse)
        );

        when(analyticsService.getWeeklyReport(userId))
                .thenReturn(ResponseEntity.ok(report));

        mockMvc.perform(get("/activitylog/analytics/user/{userId}/weekly-report", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekXp").value(180.0))
                .andExpect(jsonPath("$.previousWeekXp").value(90.0))
                .andExpect(jsonPath("$.percentageChange").value(100.0))
                .andExpect(jsonPath("$.totalActiveMinutes").value(120))
                .andExpect(jsonPath("$.topCategory").value("STUDY"));
    }
}
