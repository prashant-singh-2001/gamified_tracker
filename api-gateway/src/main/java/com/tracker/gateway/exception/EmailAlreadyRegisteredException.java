package com.tracker.gateway.exception;

/** Registration with an email that already belongs to an account. Mapped to 409. */
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Email already registered");
    }
}
