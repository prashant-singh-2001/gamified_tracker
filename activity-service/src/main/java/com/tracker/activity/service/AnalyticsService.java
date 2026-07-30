package com.tracker.activity.service;

import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface AnalyticsService {
    ResponseEntity<List<CategorySummaryResponse>> getCategorySummary(Long userId);
    ResponseEntity<List<DailyXpResponse>> getXpOverTime(Long userId, int days);
    ResponseEntity<WeeklyReportResponse> getWeeklyReport(Long userId);
}
