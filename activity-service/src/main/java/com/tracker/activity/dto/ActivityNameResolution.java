package com.tracker.activity.dto;

/**
 * Issue #66: present on an ActivityLogResponse only when the posted activityName did NOT match a
 * catalog entry exactly and was fuzzy-resolved to a different one. The substitution is never
 * silent — the client can render "logged as Running" and offer a correction.
 */
public record ActivityNameResolution(String requestedName, String resolvedName, double score) {
}
