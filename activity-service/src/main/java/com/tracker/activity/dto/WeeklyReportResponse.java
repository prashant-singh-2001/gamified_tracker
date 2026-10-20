package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.ReportMode;

import java.time.LocalDate;
import java.util.List;

/**
 * The first six components are the original contract and are unchanged. The rest only describe the
 * windows used, so a client can label the report instead of guessing:
 *
 * @param mode                {@code ROLLING} (the default) or {@code CALENDAR} (Monday to Sunday)
 * @param periodStart         first day of the period shown
 * @param periodEnd           last day of the period shown: today when rolling, the Sunday of the week
 *                            when calendar (days after today are zero in {@code dailyBreakdown})
 * @param previousPeriodStart first day of the window {@code previousWeekXp} covers
 * @param previousPeriodEnd   last day of that window. The same length as the current window up to
 *                            today, so {@code percentageChange} compares like with like
 */
public record WeeklyReportResponse(
        Double currentWeekXp,
        Double previousWeekXp,
        Double percentageChange,
        Long totalActiveMinutes,
        Category topCategory,
        List<DailyXpResponse> dailyBreakdown,
        ReportMode mode,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate previousPeriodStart,
        LocalDate previousPeriodEnd
) {}
