package com.tracker.activity.controller;

import com.tracker.activity.dao.Category;
import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.NarrativeStatus;
import com.tracker.activity.dto.WeeklyInsightsResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.service.InsightsService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightsController.class)
@DisplayName("Insights Controller Tests (issue #65)")
class InsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InsightsService insightsService;

    @Test
    @DisplayName("GET /insights/weekly reads userId from the header, not a path segment")
    void getWeeklyInsights_usesUserIdHeader() throws Exception {
        Long userId = 42L;
        WeeklyInsightsResponse response = weeklyInsightsResponse("a generated narrative", NarrativeStatus.GENERATED);
        when(insightsService.getWeeklyInsights(userId)).thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(get("/insights/weekly")
                        .header("userId", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative").value("a generated narrative"))
                .andExpect(jsonPath("$.narrativeStatus").value("GENERATED"));

        verify(insightsService).getWeeklyInsights(userId);
    }

    @Test
    @DisplayName("a null narrative serializes as null with a non-null narrativeStatus, never a missing field")
    void getWeeklyInsights_nullNarrative_serializesWithStatus() throws Exception {
        Long userId = 7L;
        WeeklyInsightsResponse response = weeklyInsightsResponse(null, NarrativeStatus.DISABLED);
        when(insightsService.getWeeklyInsights(userId)).thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(get("/insights/weekly")
                        .header("userId", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative").value(nullValue()))
                .andExpect(jsonPath("$.narrativeStatus").value("DISABLED"));
    }

    private static WeeklyInsightsResponse weeklyInsightsResponse(String narrative, NarrativeStatus status) {
        LocalDate weekStart = LocalDate.of(2026, 1, 1);
        LocalDate weekEnd = LocalDate.of(2026, 1, 7);
        WeeklyReportResponse totals = new WeeklyReportResponse(100.0, 80.0, 25.0, 200L, Category.STUDY, List.of());
        List<CategorySummaryResponse> categories = List.of(new CategorySummaryResponse(Category.STUDY, 100L, 100.0, 2L));
        return new WeeklyInsightsResponse(weekStart, weekEnd, totals, categories, narrative, status);
    }
}
