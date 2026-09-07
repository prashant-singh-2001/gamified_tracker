package com.tracker.activity.controller;

import com.tracker.activity.dto.CategorySummaryResponse;
import com.tracker.activity.dto.DailyXpResponse;
import com.tracker.activity.dto.WeeklyReportResponse;
import com.tracker.activity.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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

    // #88: ownership enforced against the trusted header inside the service (all three
    // endpoints below share the same guard -- see AnalyticsServiceImpl.requireSelf).
    @GetMapping("/user/{userId}/category-summary")
    public ResponseEntity<List<CategorySummaryResponse>> getCategorySummary(@RequestHeader("userId") Long callerUserId,
                                                                             @PathVariable Long userId) {
        return analyticsService.getCategorySummary(callerUserId, userId);
    }

    @GetMapping("/user/{userId}/xp-over-time")
    public ResponseEntity<List<DailyXpResponse>> getXpOverTime(
            @RequestHeader("userId") Long callerUserId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "7") int days) {
        return analyticsService.getXpOverTime(callerUserId, userId, days);
    }

    @GetMapping("/user/{userId}/weekly-report")
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport(@RequestHeader("userId") Long callerUserId,
                                                                 @PathVariable Long userId) {
        return analyticsService.getWeeklyReport(callerUserId, userId);
    }
}
