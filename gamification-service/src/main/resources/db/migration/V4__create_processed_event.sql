-- Issue #82: processed_event backs ActivityLoggedListener's idempotency guard but had NO Flyway
-- migration in the repo despite ddl-auto: validate -- schema drift, not something introduced here.
-- IF NOT EXISTS because the table demonstrably already exists in any running deployment (the app
-- could not start under ddl-auto: validate otherwise); this must be a no-op there.
CREATE TABLE IF NOT EXISTS processed_event (
    idempotency_key VARCHAR(255) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL
);

-- The pre-existing table's primary key is unverifiable from the repo, and ddl-auto: validate
-- never checked it -- add it defensively, only when genuinely absent, so the guard this table
-- exists to serve (a unique-constraint violation on a racing duplicate) is actually enforced.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'gamification'
          AND table_name = 'processed_event'
          AND constraint_type = 'PRIMARY KEY'
    ) THEN
        ALTER TABLE processed_event ADD PRIMARY KEY (idempotency_key);
    END IF;
END $$;
