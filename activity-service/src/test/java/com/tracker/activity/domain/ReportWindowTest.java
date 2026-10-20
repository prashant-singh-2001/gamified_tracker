package com.tracker.activity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReportWindow (issue: weekly report window)")
class ReportWindowTest {

    // 2026-01-07 is a Wednesday; its Monday is 2026-01-05 and last week's Monday is 2025-12-29, so
    // this also crosses a year boundary.
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 1, 7);

    @Test
    @DisplayName("rolling: the seven days ending today, against the seven before them")
    void rolling() {
        ReportWindow w = ReportWindow.rolling(WEDNESDAY);

        assertEquals(ReportMode.ROLLING, w.mode());
        assertEquals(LocalDate.of(2026, 1, 1), w.currentStart());
        assertEquals(WEDNESDAY, w.currentEnd());
        assertEquals(WEDNESDAY, w.displayEnd());
        assertEquals(LocalDate.of(2025, 12, 25), w.previousStart());
        assertEquals(LocalDate.of(2025, 12, 31), w.previousEnd());
    }

    @Test
    @DisplayName("rolling: windows are adjacent -- the previous one ends the day before the current starts")
    void rolling_isAdjacent() {
        ReportWindow w = ReportWindow.rolling(WEDNESDAY);
        assertEquals(w.currentStart().minusDays(1), w.previousEnd());
    }

    @Test
    @DisplayName("calendar on a Wednesday: Monday..today, against the same weekdays of last week")
    void calendar_midWeek() {
        ReportWindow w = ReportWindow.calendar(WEDNESDAY);

        assertEquals(ReportMode.CALENDAR, w.mode());
        assertEquals(LocalDate.of(2026, 1, 5), w.currentStart(), "this week's Monday");
        assertEquals(WEDNESDAY, w.currentEnd());
        assertEquals(LocalDate.of(2026, 1, 11), w.displayEnd(), "the Sunday, so all seven columns can be drawn");
        assertEquals(LocalDate.of(2025, 12, 29), w.previousStart(), "last week's Monday (across the year end)");
        assertEquals(LocalDate.of(2025, 12, 31), w.previousEnd(), "Mon..Wed of last week: like for like");
    }

    @Test
    @DisplayName("calendar on a Monday: one day against last Monday")
    void calendar_monday() {
        ReportWindow w = ReportWindow.calendar(LocalDate.of(2026, 1, 5));

        assertEquals(LocalDate.of(2026, 1, 5), w.currentStart());
        assertEquals(LocalDate.of(2026, 1, 5), w.currentEnd());
        assertEquals(LocalDate.of(2025, 12, 29), w.previousStart());
        assertEquals(LocalDate.of(2025, 12, 29), w.previousEnd());
    }

    @Test
    @DisplayName("calendar on a Sunday: the whole week against the whole previous week")
    void calendar_sunday() {
        ReportWindow w = ReportWindow.calendar(LocalDate.of(2026, 1, 11));

        assertEquals(LocalDate.of(2026, 1, 5), w.currentStart());
        assertEquals(LocalDate.of(2026, 1, 11), w.currentEnd());
        assertEquals(LocalDate.of(2026, 1, 11), w.displayEnd());
        assertEquals(LocalDate.of(2025, 12, 29), w.previousStart());
        assertEquals(LocalDate.of(2026, 1, 4), w.previousEnd(), "the full previous Monday..Sunday");
    }

    @Test
    @DisplayName("a Sunday belongs to the week that is ENDING, and the next Monday starts a fresh one")
    void calendar_weekBoundary() {
        assertEquals(LocalDate.of(2026, 1, 5), ReportWindow.calendar(LocalDate.of(2026, 1, 11)).currentStart());
        assertEquals(LocalDate.of(2026, 1, 12), ReportWindow.calendar(LocalDate.of(2026, 1, 12)).currentStart());
    }

    @Test
    @DisplayName("both modes always compare two windows of the same length")
    void windowsAreAlwaysTheSameLength() {
        for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2026, 3, 15)); d = d.plusDays(1)) {
            for (ReportMode mode : ReportMode.values()) {
                ReportWindow w = ReportWindow.of(mode, d);
                long current = ChronoUnit.DAYS.between(w.currentStart(), w.currentEnd());
                long previous = ChronoUnit.DAYS.between(w.previousStart(), w.previousEnd());
                assertEquals(current, previous, mode + " on " + d);
                assertFalse(w.previousEnd().isAfter(w.currentStart()), mode + " windows must not overlap on " + d);
            }
        }
    }

    @Test
    @DisplayName("calendar weeks always start on a Monday, whatever day today is")
    void calendarAlwaysStartsOnAMonday() {
        for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2026, 3, 15)); d = d.plusDays(1)) {
            ReportWindow w = ReportWindow.calendar(d);
            assertEquals(java.time.DayOfWeek.MONDAY, w.currentStart().getDayOfWeek(), "on " + d);
            assertEquals(java.time.DayOfWeek.SUNDAY, w.displayEnd().getDayOfWeek(), "on " + d);
        }
    }

    @Test
    @DisplayName("inCurrent / inPrevious are inclusive of both ends and exclusive of the gap days")
    void membership() {
        ReportWindow w = ReportWindow.calendar(WEDNESDAY); // current Jan 5..7, previous Dec 29..31

        assertTrue(w.inCurrent(LocalDate.of(2026, 1, 5)));
        assertTrue(w.inCurrent(WEDNESDAY));
        assertFalse(w.inCurrent(LocalDate.of(2026, 1, 4)));
        assertFalse(w.inCurrent(LocalDate.of(2026, 1, 8)));

        assertTrue(w.inPrevious(LocalDate.of(2025, 12, 29)));
        assertTrue(w.inPrevious(LocalDate.of(2025, 12, 31)));
        // Thursday..Sunday of last week are neither: they are past the like-for-like cut-off
        assertFalse(w.inPrevious(LocalDate.of(2026, 1, 1)));
        assertFalse(w.inPrevious(LocalDate.of(2026, 1, 4)));
        assertFalse(w.inCurrent(LocalDate.of(2026, 1, 1)));
    }

    @Test
    @DisplayName("of() routes to the right calculator")
    void of() {
        assertEquals(ReportWindow.rolling(WEDNESDAY), ReportWindow.of(ReportMode.ROLLING, WEDNESDAY));
        assertEquals(ReportWindow.calendar(WEDNESDAY), ReportWindow.of(ReportMode.CALENDAR, WEDNESDAY));
    }
}
