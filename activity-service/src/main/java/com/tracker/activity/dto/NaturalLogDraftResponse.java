package com.tracker.activity.dto;

import java.util.List;

/**
 * Response for {@code POST /activitylog/natural} (issue #70). Writes nothing -- {@code draft}, when
 * present, is exactly what the client would {@code POST} to the existing
 * {@code /activitylog/} to actually log it; this endpoint only ever previews.
 *
 * <p>{@code draft.activityName} is deliberately left as the raw text the model/resolver produced,
 * NOT pre-resolved onto a catalog entry -- the commit endpoint already does exact-then-fuzzy
 * resolution itself (issue #66). {@code nameResolution}/{@code suggestions} are a preview of what
 * that resolution would do, reusing the same two DTOs the commit path's 200/404 already use, so the
 * client can show "this will log as Study" before the user commits, without the draft silently
 * carrying a name the user never typed.
 */
public record NaturalLogDraftResponse(
        ActivityLogRequest draft,
        // Short, human-readable restatement of what was understood (PARSED) or why nothing could be
        // committed (NEEDS_CLARIFICATION). Null for DISABLED/UNAVAILABLE, same as narrative is null
        // for those NarrativeStatus values (#65).
        String interpretation,
        DraftStatus status,
        ActivityNameResolution nameResolution,
        List<ActivitySuggestion> suggestions
) {
}
