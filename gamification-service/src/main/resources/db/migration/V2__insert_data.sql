-- Starter achievement catalog. Idempotent: ON CONFLICT (code) DO NOTHING so a restart
-- (or repeated V2__insert_data.sql execution) never duplicates rows — code is unique per Achievement.java.

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, is_active)
VALUES ('FIRST_STEPS', 'First Steps', 'Log your first activity.', 'ACTIVITIES_LOGGED', 1, NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, is_active)
VALUES ('XP_1000', 'Rising Star', 'Earn a total of 1000 XP across all activities.', 'TOTAL_XP', 1000, NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, is_active)
VALUES ('LEVEL_5', 'Level Up', 'Reach level 5 in any single activity.', 'REACH_LEVEL_ANY', 5, NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO achievement (code, name, description, criteria_type, threshold, activity_id, is_active)
VALUES ('DEDICATED', 'Dedicated', 'Log 50 activities.', 'ACTIVITIES_LOGGED', 50, NULL, true)
ON CONFLICT (code) DO NOTHING;

-- Overall level curve (OverallLevelThreshold): widening gaps, quick early levels for retention,
-- meaningful later ones. Idempotent: ON CONFLICT (level) DO NOTHING so restarts don't duplicate.
INSERT INTO overall_level_threshold (level, threshold) VALUES (1, 0) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (2, 100) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (3, 250) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (4, 500) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (5, 1000) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (6, 2000) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (7, 4000) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (8, 8000) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (9, 16000) ON CONFLICT (level) DO NOTHING;
INSERT INTO overall_level_threshold (level, threshold) VALUES (10, 32000) ON CONFLICT (level) DO NOTHING;
