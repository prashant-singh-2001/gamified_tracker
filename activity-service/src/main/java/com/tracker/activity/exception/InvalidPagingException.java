package com.tracker.activity.exception;

/** A paging, sorting or filter parameter on GET /activitylog/user/{id} was invalid. Mapped to 400. */
public class InvalidPagingException extends RuntimeException {
    public InvalidPagingException(String message) {
        super(message);
    }
}
