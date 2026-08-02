package com.tracker.activity.exception;

/**
 * Thrown when a logged session's duration exceeds the hard cap (issue #67, layer 1). This is a
 * rejection of impossible input, the same class as the existing {@code endTime.isAfter(startTime)}
 * guard — not the statistical layer, which quarantines rather than rejects.
 */
public class ImplausibleSessionException extends RuntimeException {

    public ImplausibleSessionException(long durationMinutes, long maxDurationMinutes) {
        super("Session duration of " + durationMinutes + " minutes exceeds the maximum allowed "
                + maxDurationMinutes + " minutes.");
    }
}
