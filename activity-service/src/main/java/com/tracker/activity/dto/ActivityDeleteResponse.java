package com.tracker.activity.dto;

/**
 * What DELETE /activity/{id} actually did.
 *
 * <p>{@link Result#DEACTIVATED}: logs or streaks reference the activity, so it was kept and only
 * hidden ({@code active=false}); history stays intact and the name stays reserved. {@link
 * Result#REMOVED}: nothing referenced it, so the row is gone and the name is free again.
 */
public record ActivityDeleteResponse(Long id, String name, Result result) {
    public enum Result {DEACTIVATED, REMOVED}
}
