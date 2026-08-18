package com.tracker.activity.domain;

import com.tracker.activity.dao.Category;

import java.time.LocalDate;
import java.util.List;

/**
 * Flat facts for one user's weekly coaching digest (issue #65), assembled entirely in Java before
 * any model call -- a {@link WeeklyDigestNarrator} may only narrate these numbers, never invent new
 * ones. The narrator never sees an {@code ActivityLog} entity or a repository.
 *
 * <p>{@code noteLines} is the user's own free text, newest-first, otherwise unfiltered and
 * uncapped -- {@link WeeklyDigestPromptBuilder} applies the note-count cap, per-note length cap, and
 * sanitization, not this record. It MUST always be treated as untrusted data, never as instructions.
 */
public record DigestFacts(
        LocalDate weekStart,
        LocalDate weekEnd,
        double currentWeekXp,
        double previousWeekXp,
        double percentageChange,
        long totalActiveMinutes,
        Category topCategory,
        List<CategoryFacts> categories,
        List<String> noteLines) {

    /** One category's weekly totals -- the per-category-per-week aggregate /weekly-report never computed. */
    public record CategoryFacts(Category category, long totalDurationMinutes, double totalXpEarned, long totalSessions) {
    }
}
