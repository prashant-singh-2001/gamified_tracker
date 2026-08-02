package com.tracker.activity.exception;

import com.tracker.activity.dao.ReviewStatus;

/**
 * Thrown when an approve/reject transition is attempted on a log that is not currently
 * {@link ReviewStatus#FLAGGED} (issue #67) — e.g. re-approving an already-approved log, which
 * would otherwise write a second outbox row and double-award XP.
 */
public class ReviewStateConflictException extends RuntimeException {

    public ReviewStateConflictException(Long logId, ReviewStatus actual) {
        super("Activity log " + logId + " is not pending review (current status: " + actual
                + "). Only FLAGGED logs can be approved or rejected.");
    }
}
