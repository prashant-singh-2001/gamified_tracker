package com.tracker.activity.dto;

/**
 * Issue #65: tells a client WHY {@code narrative} is null, since the numbers on
 * {@link WeeklyInsightsResponse} are always present regardless. {@code DISABLED} means the
 * {@code insights.enabled} flag is off (or no backend is selected via
 * {@code spring.ai.model.chat}); {@code UNAVAILABLE} means the flag is on but the model call
 * failed or timed out. Deliberately does not name which backend answered -- that is an
 * operational detail, not part of the API.
 */
public enum NarrativeStatus {
    GENERATED,
    DISABLED,
    UNAVAILABLE
}
