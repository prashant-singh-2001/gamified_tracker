CREATE TABLE manual_xp_award
(
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    xp DOUBLE PRECISION NOT NULL,
    awarded_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_manual_xp_award_target_user ON manual_xp_award(target_user_id);
