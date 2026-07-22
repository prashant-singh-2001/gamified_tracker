package com.tracker.gamification.dao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankTierTest {

    @Test
    void fromTopFraction_resolvesEachBoundaryToTheCorrectTier() {
        assertEquals(RankTier.SUMMIT, RankTier.fromTopFraction(0.0));
        assertEquals(RankTier.SUMMIT, RankTier.fromTopFraction(0.049));
        assertEquals(RankTier.PEAK, RankTier.fromTopFraction(0.05));
        assertEquals(RankTier.PEAK, RankTier.fromTopFraction(0.149));
        assertEquals(RankTier.RIDGE, RankTier.fromTopFraction(0.15));
        assertEquals(RankTier.RIDGE, RankTier.fromTopFraction(0.249));
        assertEquals(RankTier.ALPINE, RankTier.fromTopFraction(0.25));
        assertEquals(RankTier.ALPINE, RankTier.fromTopFraction(0.499));
        assertEquals(RankTier.ASCENT, RankTier.fromTopFraction(0.50));
        assertEquals(RankTier.ASCENT, RankTier.fromTopFraction(0.599));
        assertEquals(RankTier.HIGHLAND, RankTier.fromTopFraction(0.60));
        assertEquals(RankTier.HIGHLAND, RankTier.fromTopFraction(0.749));
        assertEquals(RankTier.FOOTHILL, RankTier.fromTopFraction(0.75));
        assertEquals(RankTier.FOOTHILL, RankTier.fromTopFraction(0.849));
        assertEquals(RankTier.TRAILHEAD, RankTier.fromTopFraction(0.85));
        assertEquals(RankTier.TRAILHEAD, RankTier.fromTopFraction(0.94));
        assertEquals(RankTier.BASECAMP, RankTier.fromTopFraction(0.95));
        assertEquals(RankTier.BASECAMP, RankTier.fromTopFraction(1.0));
    }

    @Test
    void fromTopFraction_rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> RankTier.fromTopFraction(-0.01));
        assertThrows(IllegalArgumentException.class, () -> RankTier.fromTopFraction(1.01));
    }
}
