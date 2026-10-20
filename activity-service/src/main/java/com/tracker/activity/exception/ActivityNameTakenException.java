package com.tracker.activity.exception;

/** Creating or renaming an activity to a name another activity already has. Mapped to 409. */
public class ActivityNameTakenException extends RuntimeException {
    public ActivityNameTakenException(String name) {
        super("An activity named '" + name + "' already exists");
    }
}
