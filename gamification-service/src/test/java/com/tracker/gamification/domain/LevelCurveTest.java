package com.tracker.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LevelCurve Tests")
class LevelCurveTest {

    private final LevelCurve curve = new LevelCurve(100.0, 1.5, 100, true);

    @Test
    @DisplayName("level 1 always requires 0 XP")
    void xpRequiredFor_level1_isZero() {
        assertEquals(0.0, curve.xpRequiredFor(1));
        assertEquals(0.0, curve.xpRequiredFor(0));
    }

    @Test
    @DisplayName("xpRequiredFor follows base * (level-1)^exponent")
    void xpRequiredFor_matchesFormula() {
        assertEquals(100.0, curve.xpRequiredFor(2), 1e-9);
        assertEquals(282.84271247461906, curve.xpRequiredFor(3), 1e-6);
        assertEquals(519.6152422706632, curve.xpRequiredFor(4), 1e-6);
        assertEquals(800.0, curve.xpRequiredFor(5), 1e-9);
        assertEquals(1118.0339887498949, curve.xpRequiredFor(6), 1e-6);
    }

    @Test
    @DisplayName("0/50/100/300/800 total XP resolve to levels 1/1/2/3/5")
    void levelFor_matchesTable() {
        assertEquals(1, curve.levelFor(0));
        assertEquals(1, curve.levelFor(50));
        assertEquals(2, curve.levelFor(100));
        assertEquals(3, curve.levelFor(300));
        assertEquals(5, curve.levelFor(800));
    }

    @Test
    @DisplayName("exact level boundaries resolve to the level they unlock, not the one before")
    void levelFor_exactBoundary_resolvesForward() {
        // xpRequiredFor(3) == 282.84271247461906 exactly — Math.pow/floor's initial guess can land
        // a hair under or over this; the correction loops in levelFor must still land on 3.
        double boundary = curve.xpRequiredFor(3);
        assertEquals(3, curve.levelFor(boundary));
        assertEquals(2, curve.levelFor(boundary - 0.01));
    }

    @Test
    @DisplayName("currentLevelXpFor is XP banked since the resolved level started")
    void currentLevelXpFor_isRemainderSinceLevelStart() {
        assertEquals(0.0, curve.currentLevelXpFor(100), 1e-9);
        assertEquals(17.15728752538094, curve.currentLevelXpFor(300), 1e-6);
        assertEquals(0.0, curve.currentLevelXpFor(800), 1e-9);
    }

    @Test
    @DisplayName("level is capped at maxLevel no matter how much XP is banked")
    void levelFor_capsAtMaxLevel() {
        LevelCurve capped = new LevelCurve(100.0, 1.5, 5, true);
        assertEquals(5, capped.levelFor(1_000_000.0));
        assertEquals(800.0, capped.xpRequiredFor(5));
        assertEquals(800.0, capped.xpRequiredFor(6));
    }

    @Test
    @DisplayName("xpRequiredFor is strictly increasing across consecutive levels")
    void xpRequiredFor_isMonotonic() {
        double previous = curve.xpRequiredFor(1);
        for (int level = 2; level <= 10; level++) {
            double next = curve.xpRequiredFor(level);
            assertTrue(next > previous, "level " + level + " should require more XP than level " + (level - 1));
            previous = next;
        }
    }
}
