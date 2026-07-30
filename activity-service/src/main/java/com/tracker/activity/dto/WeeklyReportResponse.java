package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

import java.util.List;

public record WeeklyReportResponse(
        Double currentWeekXp,
        Double previousWeekXp,
        Double percentageChange,
        Long totalActiveMinutes,
        Category topCategory,
        List<DailyXpResponse> dailyBreakdown
) {}
