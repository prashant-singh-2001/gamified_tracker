package com.tracker.gateway.exception;

/**
 * A reset token that is unknown, expired or already used. One exception and one message for all
 * three, so the response does not tell a caller which it was.
 */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("Invalid or expired reset token");
    }
}
