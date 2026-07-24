CREATE TABLE activity
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(20) NOT NULL,
    xp_multiplier DOUBLE PRECISION,
    active BOOLEAN NOT NULL,
    description TEXT,
    created_at TIMESTAMP
);

CREATE TABLE activity_log
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_minutes BIGINT,
    xp_earned DOUBLE PRECISION,
    notes TEXT,
    created_at TIMESTAMP,

    CONSTRAINT fk_activity_log_activity
        FOREIGN KEY (activity_id)
            REFERENCES activity(id)
);

CREATE TABLE outbox_event
(
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(255),
    aggregate_id BIGINT,
    event_type VARCHAR(255),
    payload TEXT,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP,
    published_at TIMESTAMP,

    CONSTRAINT uk_outbox_event_idempotency_key
        UNIQUE (idempotency_key)
);