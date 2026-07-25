-- Streaks / consecutive-day tracking (issue #12): one row per (user, activity),
-- backing com.tracker.activity.dao.ActivityStreak.
CREATE TABLE activity_streak
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    current_streak INTEGER NOT NULL,
    longest_streak INTEGER NOT NULL,
    last_activity_date DATE,

    -- one streak row per (user, activity); the leading user_id also serves findByUserId
    CONSTRAINT uk_activity_streak_user_activity
        UNIQUE (user_id, activity_id),

    CONSTRAINT fk_activity_streak_activity
        FOREIGN KEY (activity_id)
            REFERENCES activity(id)
);
