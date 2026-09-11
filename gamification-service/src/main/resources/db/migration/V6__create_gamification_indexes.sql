-- Issue #85: the gamification schema shipped with primary keys and unique constraints only.
-- Each index below backs a query that runs in production today; see the comment on each.

-- NotificationServiceImpl: the feed, the unread feed, and the unread badge count are all
-- user_id-prefixed, two of them ordered by created_at DESC. level_up_event had no index at all.
CREATE INDEX idx_level_up_event_user_created
    ON level_up_event (user_id, created_at DESC);

-- findAllByActivityId (GET /level/activity/{id}) and findActivityRanking (GET
-- /leaderboard/activity/{id}) filter on activity_id ALONE. uk_level_tracker_user_activity
-- cannot serve them: activity_id is its trailing column, not its leading one.
CREATE INDEX idx_level_tracker_activity_id
    ON level_tracker (activity_id);

-- GET /ranks/{tier}/leaderboard, and GET /ranks which calls countByTier once per tier -- nine
-- full scans of user_rank per request without this. user_rank had only its user_id primary key.
CREATE INDEX idx_user_rank_tier_total_xp
    ON user_rank (tier, total_xp DESC);
