-- Fluid ranks (issue 135): rank on recent form instead of lifetime XP, with divisions inside tiers
-- and a movement baseline.
--
-- log_xp_outcome.occurred_at is WHEN THE SESSION HAPPENED (the log end time the producer sends),
-- as opposed to applied_at, which is when the consumer processed it. Rank scoring ages XP by it, so a
-- backdated or late-approved log counts from its session time. Rows written before this migration
-- have no session time, so applied_at stands in for it.
ALTER TABLE log_xp_outcome ADD COLUMN occurred_at TIMESTAMP;
UPDATE log_xp_outcome SET occurred_at = applied_at WHERE occurred_at IS NULL;
ALTER TABLE log_xp_outcome ALTER COLUMN occurred_at SET NOT NULL;
CREATE INDEX idx_log_xp_outcome_occurred_at ON log_xp_outcome (occurred_at);

-- user_rank gains the decayed score the rank is now computed from, the division inside the tier
-- (1 = best, 3 = lowest), and a movement baseline: where the user stood when previous_at was taken.
-- Existing rows get their lifetime XP as a placeholder score; the first recompute (seconds after
-- startup) overwrites every row.
ALTER TABLE user_rank ADD COLUMN rank_score DOUBLE PRECISION;
ALTER TABLE user_rank ADD COLUMN division INTEGER;
ALTER TABLE user_rank ADD COLUMN previous_tier VARCHAR(50);
ALTER TABLE user_rank ADD COLUMN previous_division INTEGER;
ALTER TABLE user_rank ADD COLUMN previous_position INTEGER;
ALTER TABLE user_rank ADD COLUMN previous_at TIMESTAMP;
UPDATE user_rank SET rank_score = COALESCE(total_xp, 0), division = 2 WHERE rank_score IS NULL;
ALTER TABLE user_rank ALTER COLUMN rank_score SET NOT NULL;
ALTER TABLE user_rank ALTER COLUMN division SET NOT NULL;

-- Tier boards and the tier/division floors now read rank_score, so the old (tier, total_xp) index
-- no longer serves them. It is kept: countByTier and older readers still use the tier prefix.
CREATE INDEX idx_user_rank_tier_score ON user_rank (tier, rank_score DESC);
