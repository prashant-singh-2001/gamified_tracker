package com.tracker.activity.service;

import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface AnalyticsService {
    // #88: callerUserId is the trusted header identity; userId is the path subject being
    // requested. Throws OwnershipViolationException when they differ.
    ResponseEntity<List<CategorySummaryResponse>> getCategorySummary(Long callerUserId, Long userId);
    ResponseEntity<List<DailyXpResponse>> getXpOverTime(Long callerUserId, Long userId, int days);
    ResponseEntity<WeeklyReportResponse> getWeeklyReport(Long callerUserId, Long userId);
}
