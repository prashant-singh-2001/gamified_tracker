package com.tracker.activity.exception;

/** A PATCH /activity/{id} that changes nothing (no fields supplied). Mapped to 400. */
public class InvalidActivityUpdateException extends RuntimeException {
    public InvalidActivityUpdateException(String message) {
        super(message);
    }
}
