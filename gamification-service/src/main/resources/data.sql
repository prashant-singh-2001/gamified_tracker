-- Starter achievement catalog. Idempotent: ON CONFLICT (code) DO NOTHING so a restart
-- (or repeated data.sql execution) never duplicates rows — code is unique per Achievement.java.

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, active)
VALUES ('FIRST_STEPS', 'First Steps', 'Log your first activity.', 'ACTIVITIES_LOGGED', 1, NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, active)
VALUES ('XP_1000', 'Rising Star', 'Earn a total of 1000 XP across all activities.', 'TOTAL_XP', 1000, NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, active)
VALUES ('LEVEL_5', 'Level Up', 'Reach level 5 in any single activity.', 'REACH_LEVEL_ANY', 5, NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, active)
VALUES ('DEDICATED', 'Dedicated', 'Log 50 activities.', 'ACTIVITIES_LOGGED', 50, NULL, true)
ON CONFLICT (code) DO NOTHING;
