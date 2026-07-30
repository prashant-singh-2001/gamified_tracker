package com.tracker.gamification.domain;

public record LevelProgress(double xpForNextLevel, double progressPercent) {
    public static final LevelProgress MAX_LEVEL = new LevelProgress(0.0, 100.0);

    /**
     * @param totalXp             cumulative XP for this (user, activity)
     * @param currentLevelXp      XP banked since the current level started
     * @param nextLevelXpRequired cumulative XP required to reach the next level
     */
    public static LevelProgress toward(double totalXp, double currentLevelXp, double nextLevelXpRequired) {
        double bandStart = totalXp - currentLevelXp;      // cumulative XP where the current level began
        double span = nextLevelXpRequired - bandStart;    // size of the current level's XP band
        if (span <= 0) {
            return MAX_LEVEL;
        }
        double remaining = Math.max(nextLevelXpRequired - totalXp, 0.0);
        double percent = Math.min(Math.max(currentLevelXp / span * 100.0, 0.0), 100.0);
        return new LevelProgress(round2(remaining), round2(percent));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
