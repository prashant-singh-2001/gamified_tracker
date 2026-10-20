package com.tracker.activity.controller;

import com.tracker.activity.domain.ReportMode;
import com.tracker.activity.dto.BestTimeOfDayResponse;
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

    // #88: ownership enforced against the trusted header inside the service (all four
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

    // ?mode=rolling (the default, and what every client got before modes existed) is the last seven
    // days ending today; ?mode=calendar is the Monday-to-Sunday week containing today. An unknown
    // mode is a 400, not a silent fallback to the default.
    @GetMapping("/user/{userId}/weekly-report")
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport(@RequestHeader("userId") Long callerUserId,
                                                                 @PathVariable Long userId,
                                                                 @RequestParam(name = "mode", required = false) String mode) {
        return analyticsService.getWeeklyReport(callerUserId, userId, ReportMode.parse(mode));
    }

    // Issue #72: "best time of day" -- an aggregate GROUP BY hour_of_day, not a model (see the
    // issue's own "analytics, not ML" title). Folded into this controller per the issue's own
    // recommendation rather than shipped as a standalone feature.
    @GetMapping("/user/{userId}/best-time-of-day")
    public ResponseEntity<BestTimeOfDayResponse> getBestTimeOfDay(@RequestHeader("userId") Long callerUserId,
                                                                   @PathVariable Long userId) {
        return analyticsService.getBestTimeOfDay(callerUserId, userId);
    }
}
