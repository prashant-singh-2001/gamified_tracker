package com.tracker.activity.controller;

import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/activitylog/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Autowired
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/user/{userId}/category-summary")
    public ResponseEntity<List<CategorySummaryResponse>> getCategorySummary(@PathVariable Long userId) {
        return analyticsService.getCategorySummary(userId);
    }

    @GetMapping("/user/{userId}/xp-over-time")
    public ResponseEntity<List<DailyXpResponse>> getXpOverTime(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "7") int days) {
        return analyticsService.getXpOverTime(userId, days);
    }

    @GetMapping("/user/{userId}/weekly-report")
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport(@PathVariable Long userId) {
        return analyticsService.getWeeklyReport(userId);
    }
}
