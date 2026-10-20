package com.tracker.activity.domain;

import com.tracker.activity.exception.InvalidReportModeException;

import java.util.Locale;

/**
 * Which week the weekly report covers.
 *
 * <ul>
 *   <li>{@link #ROLLING} (the default, and what every existing caller gets): the last seven days
 *       ending today.</li>
 *   <li>{@link #CALENDAR}: the ISO calendar week -- Monday to Sunday -- containing today.</li>
 * </ul>
 */
public enum ReportMode {
    ROLLING,
    CALENDAR;

    /** Case-insensitive; {@code null} or blank means the default, {@link #ROLLING}. */
    public static ReportMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ROLLING;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidReportModeException(raw);
        }
    }
}
