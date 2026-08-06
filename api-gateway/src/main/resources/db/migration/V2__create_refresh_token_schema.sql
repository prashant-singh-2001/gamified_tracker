CREATE TABLE refresh_token
(
   id BIGSERIAL PRIMARY KEY,
   token VARCHAR(255) NOT NULL UNIQUE,
   user_id BIGINT NOT NULL,
   expires_at TIMESTAMP NOT NULL,
   used_at TIMESTAMP,
   revoked_at TIMESTAMP,
   is_used BOOLEAN NOT NULL DEFAULT FALSE,
   is_revoked BOOLEAN NOT NULL DEFAULT FALSE,

   CONSTRAINT fk_refresh_token_user
       FOREIGN KEY (user_id) REFERENCES user_entity(id)
           ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);