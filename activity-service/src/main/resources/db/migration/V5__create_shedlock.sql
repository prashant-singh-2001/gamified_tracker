-- Issue #82: backs @SchedulerLock on OutboxRelay.publishPending() so multiple activity-service
-- instances don't all publish the same outbox rows. Standard ShedLock JDBC schema.
CREATE TABLE shedlock (
    name       VARCHAR(64)   NOT NULL,
    lock_until TIMESTAMP(3)  NOT NULL,
    locked_at  TIMESTAMP(3)  NOT NULL,
    locked_by  VARCHAR(255)  NOT NULL,
    PRIMARY KEY (name)
);
