package com.tracker.activity.dto;

import com.tracker.activity.dao.ActivityStreak;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StreakResponse.active")
class StreakResponseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("a streak last extended today is active")
    void today() {
        assertTrue(StreakResponse.isActive(TODAY, TODAY));
    }

    @Test
    @DisplayName("a streak last extended yesterday is still active: it can be saved by logging today")
    void yesterday() {
        assertTrue(StreakResponse.isActive(TODAY.minusDays(1), TODAY));
    }

    @Test
    @DisplayName("two days ago is a broken streak")
    void twoDaysAgo() {
        assertFalse(StreakResponse.isActive(TODAY.minusDays(2), TODAY));
    }

    @Test
    @DisplayName("a long-idle streak is inactive even though its stored currentStreak is still high")
    void staleCurrentStreak() {
        var streak = ActivityStreak.builder().userId(1L).activityId(9L).currentStreak(12).longestStreak(12)
                .lastActivityDate(TODAY.minusDays(40)).build();

        var response = StreakResponse.from(streak, TODAY);

        assertFalse(response.active());
        assertEquals(12, response.currentStreak(), "the stored value is reported unchanged");
    }

    @Test
    @DisplayName("a missing last date is inactive rather than an error")
    void noLastDate() {
        assertFalse(StreakResponse.isActive(null, TODAY));
    }

    @Test
    @DisplayName("a date slightly in the future (clock skew) counts as active")
    void futureDate() {
        assertTrue(StreakResponse.isActive(TODAY.plusDays(1), TODAY));
    }

    @Test
    @DisplayName("from() copies every field and adds active")
    void fromCopiesFields() {
        var streak = ActivityStreak.builder().userId(1L).activityId(9L).currentStreak(3).longestStreak(8)
                .lastActivityDate(TODAY).build();

        var response = StreakResponse.from(streak, TODAY);

        assertEquals(new StreakResponse(9L, 3, 8, TODAY, true), response);
    }
}
