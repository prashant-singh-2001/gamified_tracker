package com.tracker.activity.exception;

import com.tracker.activity.dto.ActivitySuggestion;

import java.util.List;

/**
 * Issue #66: activityName didn't match the catalog exactly, and fuzzy resolution didn't clear the
 * auto-resolve bar either (too low a score, ambiguous, or the only close match is inactive).
 * Extends {@link ActivityNotFoundException} deliberately — the message stays byte-identical to the
 * exact-match era's 404, and any pre-existing {@code assertThrows(ActivityNotFoundException.class,
 * ...)} keeps passing even though this is now thrown instead.
 */
public class ActivityNameUnresolvedException extends ActivityNotFoundException {

    private final String requestedName;
    private final transient List<ActivitySuggestion> suggestions;

    public ActivityNameUnresolvedException(String requestedName, List<ActivitySuggestion> suggestions) {
        // Message unchanged from the exact-match era on purpose: it is the documented 404 detail.
        super("Activity not found: " + requestedName);
        this.requestedName = requestedName;
        this.suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public String getRequestedName() {
        return requestedName;
    }

    public List<ActivitySuggestion> getSuggestions() {
        return suggestions;
    }
}
