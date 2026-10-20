package com.tracker.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RankScore (recency weighting)")
class RankScoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Test
    @DisplayName("XP earned right now counts in full")
    void freshXpCountsInFull() {
        assertEquals(100.0, RankScore.decayed(100.0, NOW, NOW, 14.0), 1e-9);
    }

    @Test
    @DisplayName("one half-life halves it, two quarter it")
    void halvesEveryHalfLife() {
        assertEquals(50.0, RankScore.decayed(100.0, NOW.minusDays(14), NOW, 14.0), 1e-9);
        assertEquals(25.0, RankScore.decayed(100.0, NOW.minusDays(28), NOW, 14.0), 1e-9);
        assertEquals(12.5, RankScore.decayed(100.0, NOW.minusDays(42), NOW, 14.0), 1e-9);
    }

    @Test
    @DisplayName("the half-life is a setting: 7 days halves it in a week")
    void halfLifeIsConfigurable() {
        assertEquals(50.0, RankScore.decayed(100.0, NOW.minusDays(7), NOW, 7.0), 1e-9);
    }

    @Test
    @DisplayName("decay is continuous: a few hours barely moves it")
    void decayIsContinuous() {
        double sixHoursAgo = RankScore.decayed(100.0, NOW.minusHours(6), NOW, 14.0);

        assertTrue(sixHoursAgo < 100.0 && sixHoursAgo > 98.0, "was " + sixHoursAgo);
    }

    @Test
    @DisplayName("a time in the future (clock skew) is treated as now, never as more than face value")
    void futureTimeIsNotAmplified() {
        assertEquals(100.0, RankScore.decayed(100.0, NOW.plusDays(14), NOW, 14.0), 1e-9);
    }

    @Test
    @DisplayName("the lookback cutoff is eight half-lives back")
    void lookbackCutoff() {
        assertEquals(NOW.minusDays(112), RankScore.lookbackCutoff(NOW, 14.0));
        assertEquals(NOW.minusDays(56), RankScore.lookbackCutoff(NOW, 7.0));
    }

    @Test
    @DisplayName("what the cutoff drops is worth under half a percent")
    void whatTheCutoffDropsIsNegligible() {
        double atTheCutoff = RankScore.decayed(1.0, RankScore.lookbackCutoff(NOW, 14.0), NOW, 14.0);

        assertTrue(atTheCutoff < 0.005, "was " + atTheCutoff);
    }
}
