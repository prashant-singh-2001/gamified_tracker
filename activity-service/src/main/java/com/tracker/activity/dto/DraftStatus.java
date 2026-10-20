package com.tracker.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
 *
 * <p>{@code DISABLED} is a documented, stable part of the API contract (it is the default
 * deployment state, since the feature needs a model backend): clients may rely on it, and it must
 * keep meaning "switched off, show nothing" rather than becoming an error status.
 */
@Schema(description = "Outcome of POST /activitylog/natural. The HTTP status is always 200; this field says what "
        + "happened. PARSED: draft is a valid request body for POST /activitylog/. "
        + "NEEDS_CLARIFICATION: the text was understood but not enough to log (for example no duration); draft is "
        + "null and interpretation says what is missing. "
        + "DISABLED: the feature is switched off on this deployment (natural-log.enabled=false, the default). This "
        + "is a stable, supported state and not an error: clients should hide or disable the natural-language "
        + "input instead of showing a failure. "
        + "UNAVAILABLE: the feature is on but the model did not answer; retrying later may work. "
        + "For every status except PARSED nothing is guessed and draft is null.")
public enum DraftStatus {
    PARSED,
    NEEDS_CLARIFICATION,
    DISABLED,
    UNAVAILABLE
}
