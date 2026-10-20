package com.tracker.gamification.exception;

// A threshold write that would leave an activity's level curve non-monotonic: a higher level must
// always need strictly more cumulative XP than a lower one, or level resolution (highest level whose
// xpRequired <= totalXp) stops meaning anything. Mapped to 409 -- the request is well-formed, it just
// conflicts with the rows that already exist.
public class InvalidThresholdCurveException extends RuntimeException {
    public InvalidThresholdCurveException(String message) {
        super(message);
    }
}
