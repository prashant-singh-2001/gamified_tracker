package com.tracker.gamification.dao;

import lombok.Getter;

@Getter
public enum RankTier {
    SUMMIT(0.00, 0.05),
    PEAK(0.05, 0.15),
    RIDGE(0.15, 0.25),
    ALPINE(0.25, 0.50),
    ASCENT(0.50, 0.60),
    HIGHLAND(0.60, 0.75),
    FOOTHILL(0.75, 0.85),
    TRAILHEAD(0.85, 0.95),
    BASECAMP(0.95, 1.00);

    private final double lo;

    private final double hi;

    RankTier(double lo, double hi) {
        this.lo = lo;
        this.hi = hi;
    }

    public static RankTier fromTopFraction(double topFraction) {
        if (topFraction < 0.0 || topFraction > 1.0) {
            throw new IllegalArgumentException("topFraction must be in [0.0, 1.0]: " + topFraction);
        }

        RankTier[] tiers = values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            if (topFraction >= tiers[i].lo) {
                return tiers[i];
            }
        }
        return SUMMIT;
    }
}
