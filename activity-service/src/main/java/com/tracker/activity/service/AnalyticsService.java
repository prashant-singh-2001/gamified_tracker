package com.tracker.activity.service;

import com.tracker.activity.dto.BestTimeOfDayResponse;
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

    // Issue #72: hour-of-day XP distribution over the caller's whole history (same all-time
    // scope as getCategorySummary, not a rolling window) -- a personal tendency like "what hour
    // do I usually log the most XP" is diluted by a short window, not clarified by one.
    ResponseEntity<BestTimeOfDayResponse> getBestTimeOfDay(Long callerUserId, Long userId);
}
