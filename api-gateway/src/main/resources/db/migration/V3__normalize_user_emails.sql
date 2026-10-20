-- Emails are now stored trimmed and lower-cased (Emails.normalize), and registration/login compare
-- them that way. Backfill existing rows so accounts created before this change still log in, and
-- add a case-insensitive unique index so the database enforces what the application assumes even
-- for writers that bypass it (manual inserts, other tooling).

-- If two accounts differ only by case or surrounding whitespace we cannot pick a winner, and the
-- UPDATE below would hit uk_user_email with an opaque error. Fail with an explanation instead.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_entity
        GROUP BY lower(btrim(email))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot normalise user emails: accounts exist whose emails differ only by case or whitespace. Merge or remove the duplicates, then restart.';
    END IF;
END $$;

UPDATE user_entity
SET email = lower(btrim(email))
WHERE email <> lower(btrim(email));

CREATE UNIQUE INDEX uk_user_email_lower ON user_entity (lower(email));
