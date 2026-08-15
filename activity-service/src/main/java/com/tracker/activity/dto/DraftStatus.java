package com.tracker.activity.dto;

/**
 * Outcome of {@code POST /activitylog/natural} (issue #70). Same always-200-plus-status shape as
 * {@link NarrativeStatus} (#65): one wire contract for "feature off," "couldn't understand it," and
 * "backend down," rather than a second response shape or varying HTTP status codes.
 *
 * <p>{@code PARSED} means {@code draft} is present and valid input to the existing
 * {@code POST /activitylog/}. {@code NEEDS_CLARIFICATION} means the model answered but
 * {@code LogIntentResolver} rejected the result (e.g. no stated duration) -- {@code draft} is null,
 * nothing was guessed. {@code DISABLED}/{@code UNAVAILABLE} mirror {@code NarrativeStatus} exactly:
 * flag off (or no backend selected) vs. flag on but the model call failed.
 */
public enum DraftStatus {
    PARSED,
    NEEDS_CLARIFICATION,
    DISABLED,
    UNAVAILABLE
}
