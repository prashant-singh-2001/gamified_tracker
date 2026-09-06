package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

import java.util.List;

// Issue #72: "you tend to log the most XP in category X around hour Y" -- analytics, not ML.
// hourlyBreakdown is the raw GROUP BY hour_of_day the issue asks for (24 zero-filled buckets,
// all categories combined); bestHour/bestCategory/bestCategoryHour are the one-sentence summary
// derived from it, mirroring WeeklyReportResponse.topCategory's nullable-when-no-logs shape.
public record BestTimeOfDayResponse(
        List<HourOfDayXpResponse> hourlyBreakdown,
        Integer bestHour,
        Category bestCategory,
        Integer bestCategoryHour
) {}
