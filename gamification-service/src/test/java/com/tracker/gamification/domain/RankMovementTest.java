package com.tracker.gamification.domain;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.domain.RankMovement.Trend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RankMovement")
class RankMovementTest {

    @Test
    @DisplayName("no baseline is NEW, and there is no position change")
    void noBaselineIsNew() {
        assertEquals(Trend.NEW, RankMovement.trend(RankTier.ALPINE, 2, 10, null, null, null));
        assertNull(RankMovement.positionChange(10, null));
    }

    @Test
    @DisplayName("positionChange is places gained: positive means up the table")
    void positionChangeIsPlacesGained() {
        assertEquals(6, RankMovement.positionChange(10, 16));
        assertEquals(-4, RankMovement.positionChange(10, 6));
        assertEquals(0, RankMovement.positionChange(10, 10));
    }

    @Test
    @DisplayName("a better tier is PROMOTED, a worse one DEMOTED, whatever the positions did")
    void tierChangeIsPromotionOrDemotion() {
        assertEquals(Trend.PROMOTED, RankMovement.trend(RankTier.PEAK, 3, 30, RankTier.RIDGE, 1, 20));
        assertEquals(Trend.DEMOTED, RankMovement.trend(RankTier.RIDGE, 1, 20, RankTier.PEAK, 3, 30));
    }

    @Test
    @DisplayName("a division change inside one tier is PROMOTED or DEMOTED too (1 is the best division)")
    void divisionChangeIsPromotionOrDemotion() {
        assertEquals(Trend.PROMOTED, RankMovement.trend(RankTier.ALPINE, 1, 8, RankTier.ALPINE, 2, 9));
        assertEquals(Trend.DEMOTED, RankMovement.trend(RankTier.ALPINE, 3, 12, RankTier.ALPINE, 2, 9));
    }

    @Test
    @DisplayName("on the same tier and division, position decides UP / DOWN / SAME")
    void sameStepFallsBackToPosition() {
        assertEquals(Trend.UP, RankMovement.trend(RankTier.ALPINE, 2, 8, RankTier.ALPINE, 2, 11));
        assertEquals(Trend.DOWN, RankMovement.trend(RankTier.ALPINE, 2, 11, RankTier.ALPINE, 2, 8));
        assertEquals(Trend.SAME, RankMovement.trend(RankTier.ALPINE, 2, 9, RankTier.ALPINE, 2, 9));
    }

    @Test
    @DisplayName("step orders every tier and division best-first: SUMMIT I is 0")
    void stepOrdersBestFirst() {
        assertEquals(0, RankMovement.step(RankTier.SUMMIT, 1));
        assertEquals(2, RankMovement.step(RankTier.SUMMIT, 3));
        assertEquals(3, RankMovement.step(RankTier.PEAK, 1));
        assertEquals(26, RankMovement.step(RankTier.BASECAMP, 3));
        assertTrue(RankMovement.step(RankTier.PEAK, 3) < RankMovement.step(RankTier.RIDGE, 1));
    }
}
