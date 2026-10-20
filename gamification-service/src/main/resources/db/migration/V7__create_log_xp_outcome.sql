-- One row per activity log whose XP gamification has applied, written in the SAME transaction as
-- the XP mutation (ActivityLoggedListener -> LevelTrackerServiceImpl.saveForLog). It backs
-- GET /activitylog/{id}/outcome, which lets a client learn "was my log applied, and did it level me
-- up?" without polling notifications. It is also the first per-log XP ledger in this service.
--
-- previous_level/new_level are the ACTIVITY's level (level_tracker.level) before and after the
-- award, the same level a LevelUpEvent reports -- not the overall level.
-- Logs processed before this migration have no row here and will read as PENDING forever.
CREATE TABLE log_xp_outcome (
    log_id          BIGINT           PRIMARY KEY,
    user_id         BIGINT           NOT NULL,
    activity_id     BIGINT           NOT NULL,
    xp_applied      DOUBLE PRECISION NOT NULL,
    previous_level  INTEGER          NOT NULL,
    new_level       INTEGER          NOT NULL,
    leveled_up      BOOLEAN          NOT NULL,
    applied_at      TIMESTAMP        NOT NULL
);
