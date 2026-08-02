-- Session integrity (issue #67): quarantine state for suspicious activity logs.
-- CLEARED  = passed all checks; XP awarded normally (the default for every existing row)
-- FLAGGED  = statistical outlier; NO outbox row was written, so XP is withheld pending review
-- APPROVED = a maintainer cleared it; the outbox row was written at approval time
-- REJECTED = a maintainer rejected it; XP is never awarded
-- NOT NULL DEFAULT backfills every pre-existing row as CLEARED in one statement.
ALTER TABLE activity_log
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'CLEARED';

-- Drives the admin review queue (WHERE review_status = 'FLAGGED' ORDER BY created_at DESC).
CREATE INDEX idx_activity_log_review_status
    ON activity_log (review_status);

-- The detector's baseline query is
--   WHERE user_id = ? AND activity_id IN (...) AND review_status IN (...) ORDER BY created_at DESC
-- and activity_log currently has only idx_activity_log_user_id on (user_id) alone.
CREATE INDEX idx_activity_log_user_id_created_at
    ON activity_log (user_id, created_at);
