package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

public record CategorySummaryResponse(
        Category category,
        Long totalDurationMinutes,
        Double totalXpEarned,
        Long totalSessions
) {}
