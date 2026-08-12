package com.tracker.activity.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Issue #65. Always {@code 200} -- {@code totals}/{@code categories} are computed in Java and always
 * present; only {@code narrative} depends on the AI backend, and {@code narrativeStatus} says why
 * it's null when it is, so a client never needs to special-case a different status code or shape.
 */
public record WeeklyInsightsResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        WeeklyReportResponse totals,
        List<CategorySummaryResponse> categories,
        String narrative,
        NarrativeStatus narrativeStatus) {
}
