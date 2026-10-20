package com.tracker.activity.domain;

import com.tracker.activity.exception.InvalidReportModeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportModeTest {

    @Test
    void absentOrBlankIsTheRollingDefault() {
        assertEquals(ReportMode.ROLLING, ReportMode.parse(null));
        assertEquals(ReportMode.ROLLING, ReportMode.parse(""));
        assertEquals(ReportMode.ROLLING, ReportMode.parse("   "));
    }

    @Test
    void parsesCaseInsensitivelyAndTrimmed() {
        assertEquals(ReportMode.CALENDAR, ReportMode.parse("calendar"));
        assertEquals(ReportMode.CALENDAR, ReportMode.parse("CALENDAR"));
        assertEquals(ReportMode.CALENDAR, ReportMode.parse(" Calendar "));
        assertEquals(ReportMode.ROLLING, ReportMode.parse("rolling"));
    }

    @Test
    void anUnknownModeIsRejectedNotSilentlyDefaulted() {
        InvalidReportModeException ex = assertThrows(InvalidReportModeException.class, () -> ReportMode.parse("monthly"));

        assertEquals("mode must be one of: rolling, calendar (got 'monthly')", ex.getMessage());
    }

    @Test
    void localeCannotBreakParsing() {
        // "calendar".toUpperCase() in a Turkish locale turns i into a dotted capital I
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals(ReportMode.CALENDAR, ReportMode.parse("calendar"));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
