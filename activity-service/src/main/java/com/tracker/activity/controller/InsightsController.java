package com.tracker.activity.controller;

import com.tracker.activity.dto.WeeklyInsightsResponse;
import com.tracker.activity.service.InsightsService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/insights")
public class InsightsController {

    private final InsightsService insightsService;

    // @RequestHeader, never @PathVariable: the response is derived from the user's private
    // free-text notes, so it must come from the gateway-injected, JWT-trusted userId, not a
    // client-suppliable path segment (see UserIdHeaderFilter -- a forged header can't survive it).
    @GetMapping("/weekly")
    public ResponseEntity<WeeklyInsightsResponse> getWeeklyInsights(@RequestHeader("userId") Long userId) {
        return insightsService.getWeeklyInsights(userId);
    }
}
