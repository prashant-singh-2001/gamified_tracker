package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;
import com.tracker.activity.domain.MatchField;

/** One ranked "did you mean" entry on a 404 from POST /activitylog/ (issue #66). */
public record ActivitySuggestion(
        String name,
        Category category,
        // Inactive activities ARE suggested (#7): a user who typo'd the name of a soft-deleted
        // activity needs to be told "that one exists but is disabled", not "no such activity".
        // They are never auto-resolved onto — see ActivityMatcher.Reason.INACTIVE_TOP_MATCH.
        boolean active,
        double score,
        MatchField matchedOn
) {
}
