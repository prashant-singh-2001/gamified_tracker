package com.tracker.gamification.exception;

// #76/#88: thrown when the caller (from the trusted "userId" header) asks for a resource that is
// explicitly keyed by someone else's userId in the request itself (e.g. GET /level/user/{userId}).
// Kept distinct from a not-found case: here the subject is already named in the URL, so a 403
// leaks nothing a 404 wouldn't also require explaining. Deliberately service-specific rather than
// a shared type in `contracts` -- that module is for cross-service wire contracts, not a security
// concern local to one service's authorization layer.
public class OwnershipViolationException extends RuntimeException {
    public OwnershipViolationException(String message) {
        super(message);
    }
}
