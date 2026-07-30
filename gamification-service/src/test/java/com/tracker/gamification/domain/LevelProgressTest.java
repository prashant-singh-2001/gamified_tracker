package com.tracker.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("LevelProgress Tests")
class LevelProgressTest {

    @Test
    @DisplayName("mid-band: 640 total XP in a 500->1000 band is 28% with 360 XP remaining")
    void midBand() {
        // bandStart = 640 - 140 = 500, span = 1000 - 500 = 500
        LevelProgress progress = LevelProgress.toward(640.0, 140.0, 1000.0);

        assertEquals(360.0, progress.xpForNextLevel());
        assertEquals(28.0, progress.progressPercent());
    }

    @Test
    @DisplayName("start of a band is 0% progress")
    void bandStart() {
        LevelProgress progress = LevelProgress.toward(500.0, 0.0, 1000.0);

        assertEquals(500.0, progress.xpForNextLevel());
        assertEquals(0.0, progress.progressPercent());
    }

    @Test
    @DisplayName("edge of a band is 100% progress with 0 XP remaining")
    void bandEdge() {
        LevelProgress progress = LevelProgress.toward(1000.0, 500.0, 1000.0);

        assertEquals(0.0, progress.xpForNextLevel());
        assertEquals(100.0, progress.progressPercent());
    }

    @Test
    @DisplayName("non-positive span (no threshold ahead / malformed data) reports MAX_LEVEL")
    void nonPositiveSpan() {
        LevelProgress progress = LevelProgress.toward(1200.0, 200.0, 1000.0);

        assertEquals(LevelProgress.MAX_LEVEL, progress);
    }
}
