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

    /** Each tier band is split into this many divisions, numbered 1 (best) to DIVISIONS (lowest). */
    public static final int DIVISIONS = 3;

    /**
     * The division inside this tier for a top fraction: the band [lo, hi) is cut into equal thirds,
     * the top third being division 1. A fraction outside the band is clamped to it.
     */
    public int divisionFor(double topFraction) {
        double within = withinBand(topFraction);
        return Math.min(DIVISIONS, (int) (within * DIVISIONS) + 1);
    }

    /**
     * How far up its division a top fraction sits: 0 at the bottom edge (about to drop to the division
     * below), 1 at the top edge (about to be promoted). Handy for a progress bar.
     */
    public double divisionProgress(double topFraction) {
        double scaled = withinBand(topFraction) * DIVISIONS;
        double intoDivision = scaled - Math.floor(scaled);
        return 1.0 - intoDivision;
    }

    // 0 at the best edge of the band, approaching 1 at the worst edge.
    private double withinBand(double topFraction) {
        double clamped = Math.min(Math.max(topFraction, lo), Math.nextDown(hi));
        return (clamped - lo) / (hi - lo);
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
