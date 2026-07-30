package com.tracker.gamification.domain;

/**
 * Default XP -> level progression curve, used when an activity has no explicit
 * {@code activity_level_threshold} rows: {@code xpRequiredFor(level) = baseXp * (level - 1) ^ exponent}.
 * Level 1 always starts at 0 XP. Plain arithmetic, no Spring/DB dependency, so callers can compute a
 * level without a threshold row existing anywhere.
 */
public class LevelCurve {

    private final double baseXp;
    private final double exponent;
    private final int maxLevel;
    private final boolean enabled;

    public LevelCurve(double baseXp, double exponent, int maxLevel, boolean enabled) {
        this.baseXp = baseXp;
        this.exponent = exponent;
        this.maxLevel = maxLevel;
        this.enabled = enabled;
    }

    /** Cumulative XP required to reach {@code level}. Level 1 (or below) always requires 0. */
    public double xpRequiredFor(int level) {
        if (level <= 1) {
            return 0.0;
        }
        int cappedLevel = Math.min(level, maxLevel);
        return baseXp * Math.pow(cappedLevel - 1, exponent);
    }

    /** The level this much total XP has reached, capped at {@code maxLevel}. */
    public int levelFor(double totalXp) {
        if (totalXp <= 0) {
            return 1;
        }

        int level = 1 + (int) Math.floor(Math.pow(totalXp / baseXp, 1.0 / exponent));
        level = Math.max(1, Math.min(level, maxLevel));

        // Math.pow/floor can land a hair off an exact boundary — nudge onto the correct side.
        while (level < maxLevel && xpRequiredFor(level + 1) <= totalXp) {
            level++;
        }
        while (level > 1 && xpRequiredFor(level) > totalXp) {
            level--;
        }
        return level;
    }

    /** XP banked since the resolved level started. */
    public double currentLevelXpFor(double totalXp) {
        int level = levelFor(totalXp);
        return totalXp - xpRequiredFor(level);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /** Kill switch — lets the old "unseeded activity stays level 1 forever" behaviour be restored
     * by config alone, no code rollback. */
    public boolean isEnabled() {
        return enabled;
    }
}
