package com.tracker.activity.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The dates a weekly report covers, as a pure function of "today" (so it is testable without a
 * clock).
 *
 * <p>Two windows are compared, and they are always <b>the same length</b>:
 * <ul>
 *   <li>the <i>current</i> window, {@code currentStart..today}, which is what the totals and the
 *       percentage change are computed over;</li>
 *   <li>the <i>previous</i> window, {@code previousStart..previousEnd}, the same number of days
 *       earlier.</li>
 * </ul>
 * Equal length matters in {@link ReportMode#CALENDAR}: on a Tuesday "this week so far" (two days)
 * compared with a FULL previous week would read as a slump every Monday-to-Saturday. So the previous
 * window is the same weekdays of last week, like for like.
 *
 * <p>{@code displayEnd} is the last day of the period being shown: today when rolling, the Sunday of
 * the week when calendar -- so a client can draw all seven columns, with days after today empty.
 */
public record ReportWindow(ReportMode mode,
                           LocalDate currentStart, LocalDate currentEnd, LocalDate displayEnd,
                           LocalDate previousStart, LocalDate previousEnd) {

    public static ReportWindow of(ReportMode mode, LocalDate today) {
        return mode == ReportMode.CALENDAR ? calendar(today) : rolling(today);
    }

    /** The seven days ending today, against the seven before them. */
    public static ReportWindow rolling(LocalDate today) {
        LocalDate currentStart = today.minusDays(6);
        return new ReportWindow(ReportMode.ROLLING, currentStart, today, today,
                currentStart.minusDays(7), today.minusDays(7));
    }

    /**
     * Monday of today's week through today, against the same weekdays of the previous week. The ISO
     * week starts on Monday regardless of locale or JVM default.
     */
    public static ReportWindow calendar(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long elapsedDays = ChronoUnit.DAYS.between(monday, today); // 0 on Monday .. 6 on Sunday
        LocalDate previousMonday = monday.minusDays(7);
        return new ReportWindow(ReportMode.CALENDAR, monday, today, monday.plusDays(6),
                previousMonday, previousMonday.plusDays(elapsedDays));
    }

    public boolean inCurrent(LocalDate date) {
        return !date.isBefore(currentStart) && !date.isAfter(currentEnd);
    }

    public boolean inPrevious(LocalDate date) {
        return !date.isBefore(previousStart) && !date.isAfter(previousEnd);
    }
}
