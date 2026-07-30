package com.tracker.activity.exception;

/**
 * Thrown when a client attempts to log time against a soft-deleted (inactive) activity.
 *
 * <p>An activity is disabled by setting {@code Activity.active = false}.
 * Logging against a disabled activity must be rejected at the service layer
 * before any XP or streak side-effects are applied (issue #7).
 */
public class InactiveActivityException extends RuntimeException {

    public InactiveActivityException(String activityName) {
        super("Activity '" + activityName + "' is inactive and cannot accept new log entries. "
                + "Re-enable the activity before logging time against it.");
    }
}
