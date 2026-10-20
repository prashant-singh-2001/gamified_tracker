package com.tracker.gamification.dto;

/**
 * The result of {@code GET /activitylog/{id}/outcome}. Always the same five keys so a typed client
 * can parse it without branching; the XP/level fields are null while the status is PENDING.
 *
 * <p>PENDING means "gamification has not applied XP for this log" -- which covers a log still in
 * flight through the outbox, one held for review (FLAGGED/REJECTED never reach gamification;
 * APPROVED ones do), and, deliberately, an id that is not yours or does not exist (so the endpoint
 * cannot be used to probe other users' logs). Levels are the activity's level, not the overall one.
 */
public record LogOutcomeDto(
        Status status,
        Double xpApplied,
        Boolean leveledUp,
        Integer newLevel,
        Integer previousLevel
) {
    public enum Status {PENDING, APPLIED}

    public static LogOutcomeDto pending() {
        return new LogOutcomeDto(Status.PENDING, null, null, null, null);
    }

    public static LogOutcomeDto applied(double xpApplied, boolean leveledUp, int newLevel, int previousLevel) {
        return new LogOutcomeDto(Status.APPLIED, xpApplied, leveledUp, newLevel, previousLevel);
    }
}
