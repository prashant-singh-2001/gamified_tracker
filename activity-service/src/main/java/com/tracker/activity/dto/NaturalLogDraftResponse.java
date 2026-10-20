package com.tracker.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(nullable = true, description = "Exactly the body to POST to /activitylog/ to log this. Present only "
                + "when status is PARSED; null for every other status.")
        ActivityLogRequest draft,
        // Short, human-readable restatement of what was understood (PARSED) or why nothing could be
        // committed (NEEDS_CLARIFICATION). Null for DISABLED/UNAVAILABLE, same as narrative is null
        // for those NarrativeStatus values (#65).
        @Schema(nullable = true, description = "Short restatement of what was understood (PARSED) or what is "
                + "missing (NEEDS_CLARIFICATION). Null for DISABLED and UNAVAILABLE.")
        String interpretation,
        DraftStatus status,
        @Schema(nullable = true, description = "Preview of how draft.activityName would resolve when committed. "
                + "Null unless a fuzzy substitution would happen.")
        ActivityNameResolution nameResolution,
        @Schema(description = "Close catalog matches for draft.activityName. Empty (never null) when there is "
                + "nothing to suggest, including for DISABLED.")
        List<ActivitySuggestion> suggestions
) {
}
