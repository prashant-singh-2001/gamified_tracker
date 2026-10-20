package com.tracker.gamification.domain;

import com.tracker.gamification.dao.RankTier;

/**
 * Movement since a baseline: how a user's rank changed compared with where they stood when the
 * baseline was taken (see UserRank.previous*). Pure functions, exact to test.
 */
public final class RankMovement {

    /** NEW: no baseline yet. PROMOTED/DEMOTED: the tier or division changed. UP/DOWN/SAME: same step, position moved. */
    public enum Trend {PROMOTED, DEMOTED, UP, DOWN, SAME, NEW}

    private RankMovement() {
    }

    /** Places gained since the baseline: positive is up the table. {@code null} when there is no baseline. */
    public static Integer positionChange(int position, Integer previousPosition) {
        return previousPosition == null ? null : previousPosition - position;
    }

    public static Trend trend(RankTier tier, int division, int position,
                              RankTier previousTier, Integer previousDivision, Integer previousPosition) {
        if (previousTier == null || previousDivision == null || previousPosition == null) {
            return Trend.NEW;
        }
        int now = step(tier, division);
        int before = step(previousTier, previousDivision);
        if (now < before) {
            return Trend.PROMOTED;
        }
        if (now > before) {
            return Trend.DEMOTED;
        }
        int change = previousPosition - position;
        return change > 0 ? Trend.UP : change < 0 ? Trend.DOWN : Trend.SAME;
    }

    /** One number for "how good is this tier and division": 0 is SUMMIT I, higher is worse. */
    public static int step(RankTier tier, int division) {
        return tier.ordinal() * RankTier.DIVISIONS + (division - 1);
    }
}
