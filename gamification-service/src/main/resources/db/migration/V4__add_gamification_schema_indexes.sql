CREATE INDEX idx_level_up_event_user_id ON level_up_event (user_id, created_at DESC);
CREATE INDEX idx_level_up_event_user_read ON level_up_event (user_id, is_read, created_at DESC);
CREATE INDEX idx_level_tracker_archive_user_id ON level_tracker_archive (user_id, activity_id, archived_at DESC);
CREATE INDEX idx_user_achievement_user_id ON user_achievement (user_id, unlocked_at DESC);
