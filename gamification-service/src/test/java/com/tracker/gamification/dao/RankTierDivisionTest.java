package com.tracker.gamification.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RankTier divisions")
class RankTierDivisionTest {

    @Test
    @DisplayName("every tier band is cut into thirds, the top third being division 1")
    void bandIsCutIntoThirds() {
        // ALPINE is [0.25, 0.50), thirds of width 1/12
        assertEquals(1, RankTier.ALPINE.divisionFor(0.25));
        assertEquals(1, RankTier.ALPINE.divisionFor(0.32));
        assertEquals(2, RankTier.ALPINE.divisionFor(0.34));
        assertEquals(2, RankTier.ALPINE.divisionFor(0.41));
        assertEquals(3, RankTier.ALPINE.divisionFor(0.42));
        assertEquals(3, RankTier.ALPINE.divisionFor(0.499));
    }

    @Test
    @DisplayName("the narrow 5% tiers split too")
    void narrowTiersSplit() {
        // SUMMIT is [0, 0.05): thirds of width 1/60
        assertEquals(1, RankTier.SUMMIT.divisionFor(0.0));
        assertEquals(2, RankTier.SUMMIT.divisionFor(0.02));
        assertEquals(3, RankTier.SUMMIT.divisionFor(0.04));
    }

    @Test
    @DisplayName("a fraction outside the band is clamped into it, never out of range")
    void outsideTheBandIsClamped() {
        assertEquals(1, RankTier.PEAK.divisionFor(0.0));
        assertEquals(3, RankTier.PEAK.divisionFor(0.9));
        for (RankTier tier : RankTier.values()) {
            for (double f = 0.0; f <= 1.0; f += 0.013) {
                int d = tier.divisionFor(f);
                assertTrue(d >= 1 && d <= RankTier.DIVISIONS, tier + " at " + f + " gave " + d);
            }
        }
    }

    @Test
    @DisplayName("fromTopFraction and divisionFor agree: the division belongs to the tier the fraction maps to")
    void divisionsAreConsistentWithTheTierLookup() {
        for (double f = 0.0; f < 1.0; f += 0.007) {
            RankTier tier = RankTier.fromTopFraction(f);
            assertTrue(f >= tier.getLo() && f < tier.getHi() + 1e-12, "tier " + tier + " does not contain " + f);
            assertTrue(tier.divisionFor(f) >= 1 && tier.divisionFor(f) <= 3);
        }
    }

    @Test
    @DisplayName("divisionProgress: 0 at the bottom edge of a division, rising to 1 at its top edge")
    void progressRisesTowardsPromotion() {
        // SUMMIT division 1 is [0, 1/60): best edge f=0 is the top (progress 1), the far edge is the bottom (0)
        assertEquals(1.0, RankTier.SUMMIT.divisionProgress(0.0), 1e-9);
        assertEquals(0.5, RankTier.SUMMIT.divisionProgress(1.0 / 120), 1e-9);
        assertEquals(0.0, RankTier.SUMMIT.divisionProgress(Math.nextDown(1.0 / 60)), 1e-6);
    }

    @Test
    @DisplayName("progress always stays within 0..1")
    void progressIsBounded() {
        for (RankTier tier : RankTier.values()) {
            for (double f = tier.getLo(); f < tier.getHi(); f += 0.003) {
                double p = tier.divisionProgress(f);
                assertTrue(p >= 0.0 && p <= 1.0, tier + " at " + f + " gave " + p);
            }
        }
    }
}
