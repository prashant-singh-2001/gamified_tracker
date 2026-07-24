CREATE TABLE achievement
(
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255),
    name VARCHAR(255),
    description TEXT,
    criteria_type VARCHAR(50),
    threshold BIGINT,
    activity_id BIGINT,
    is_active BOOLEAN,

    CONSTRAINT uk_achievement_code
        UNIQUE(code)
);

CREATE TABLE activity_level_threshold
(
    activity_id BIGINT NOT NULL,
    level INTEGER NOT NULL,
    xp_required DOUBLE PRECISION NOT NULL,

    CONSTRAINT pk_activity_level_threshold
        PRIMARY KEY (activity_id, level)
);

CREATE TABLE level_tracker
(
    id BIGSERIAL PRIMARY KEY ,
    user_id BIGINT,
    activity_id BIGINT,
    level INTEGER,
    total_xp DOUBLE PRECISION,
    current_level_xp DOUBLE PRECISION,
    log_count INTEGER,

    CONSTRAINT uk_level_tracker_user_activity
        UNIQUE(user_id, activity_id)
);

CREATE TABLE level_tracker_archive
(
    id BIGSERIAL PRIMARY KEY ,
    user_id BIGINT,
    activity_id BIGINT,
    level INTEGER,
    total_xp DOUBLE PRECISION,
    current_level_xp DOUBLE PRECISION,
    archived_at TIMESTAMP
);

CREATE TABLE level_up_event
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    activity_id BIGINT,
    old_level INTEGER,
    new_level INTEGER,
    total_xp DOUBLE PRECISION,
    current_level_xp DOUBLE PRECISION,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP
);

CREATE TABLE user_achievement
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    achievement_id BIGINT,
    unlocked_at TIMESTAMP,

    CONSTRAINT uk_user_achievement
        UNIQUE(user_id, achievement_id)
);

CREATE TABLE overall_level_threshold
(
    level BIGSERIAL PRIMARY KEY,
    threshold DOUBLE PRECISION
);

CREATE TABLE user_rank (
   user_id BIGINT PRIMARY KEY,
   total_xp DOUBLE PRECISION,
   overall_level INTEGER,
   tier VARCHAR(50),
   percentile DOUBLE PRECISION,
   position INTEGER,
   total_users INTEGER,
   updated_at TIMESTAMP
);