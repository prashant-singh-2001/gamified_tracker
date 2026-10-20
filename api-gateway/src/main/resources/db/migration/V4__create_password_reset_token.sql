-- One row per issued password-reset token. Only the SHA-256 of the token is stored, so a database
-- read does not yield usable reset links. A token is single use (used_at is set atomically when it
-- is redeemed) and expires at expires_at.
CREATE TABLE password_reset_token
(
    id         BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL,

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id) REFERENCES user_entity (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);
