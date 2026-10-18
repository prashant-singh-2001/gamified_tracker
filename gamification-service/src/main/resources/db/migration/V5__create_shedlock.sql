-- Issue #82: backs @SchedulerLock on RankRecomputeServiceImpl.recompute() so multiple
-- gamification-service instances don't all rebuild the leaderboard on the same tick. Standard
-- ShedLock JDBC schema.
CREATE TABLE shedlock (
    name       VARCHAR(64)   NOT NULL,
    lock_until TIMESTAMP(3)  NOT NULL,
    locked_at  TIMESTAMP(3)  NOT NULL,
    locked_by  VARCHAR(255)  NOT NULL,
    PRIMARY KEY (name)
);
