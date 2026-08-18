package com.tracker.activity.service;

import com.tracker.activity.dto.WeeklyInsightsResponse;
import org.springframework.http.ResponseEntity;

public interface InsightsService {
    ResponseEntity<WeeklyInsightsResponse> getWeeklyInsights(Long userId);
}
