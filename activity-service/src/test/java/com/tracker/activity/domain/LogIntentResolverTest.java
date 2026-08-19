package com.tracker.activity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure Java, no Spring, no model -- this is where the actual correctness of "this morning" lives
 * (issue #70). {@link LogIntentResolver}'s one invariant is that a resolved draft's times can never
 * land in the future; most cases here exist to pin that under a fixed {@link Clock}.
 */
@DisplayName("LogIntentResolver (issue #70)")
class LogIntentResolverTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final long MAX_DURATION_MINUTES = 1440;

    private LogIntentResolver resolverAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new LogIntentResolver(clock, MAX_DURATION_MINUTES);
    }

    private ParsedLogIntent intent(String name, int dayOffset, Integer hour, Integer minute,
                                    TimeOfDay timeOfDay, Integer duration, String notes) {
        return new ParsedLogIntent(name, dayOffset, hour, minute, timeOfDay, duration, notes);
    }

    @Test
    @DisplayName("explicit time-of-day, well before now, resolves as stated")
    void timeOfDay_beforeNow_resolvesAsStated() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 14, 0);
        LogIntentResolver resolver = resolverAt(now);

        var resolution = resolver.resolve(
                intent("studying", 0, null, null, TimeOfDay.MORNING, 90, "Spring Boot"));

        assertTrue(resolution.resolved());
        assertEquals(LogIntentResolver.Reason.RESOLVED, resolution.reason());
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), resolution.draft().startTime());
        assertEquals(LocalDateTime.of(2026, 8, 14, 10, 30), resolution.draft().endTime());
        assertEquals("studying", resolution.draft().activityName());
        assertEquals("Spring Boot", resolution.draft().notes());
    }

    @Test
    @DisplayName("explicit clock time on a past day resolves as stated")
    void explicitClockTime_pastDay_resolvesAsStated() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(intent("running", -1, 19, 30, null, 60, null));

        assertTrue(resolution.resolved());
        assertEquals(LocalDateTime.of(2026, 8, 13, 19, 30), resolution.draft().startTime());
        assertEquals(LocalDateTime.of(2026, 8, 13, 20, 30), resolution.draft().endTime());
    }

    @Test
    @DisplayName("explicit hour wins over timeOfDay when both are present")
    void explicitHour_winsOverTimeOfDay() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 23, 0));

        var resolution = resolver.resolve(intent("reading", 0, 8, 0, TimeOfDay.EVENING, 30, null));

        assertEquals(LocalDateTime.of(2026, 8, 14, 8, 0), resolution.draft().startTime());
    }

    @Test
    @DisplayName("no day and no time at all anchors to \"just finished\"")
    void noDayNoTime_anchorsToNow() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 15, 0);
        LogIntentResolver resolver = resolverAt(now);

        var resolution = resolver.resolve(intent("studying", 0, null, null, null, 30, null));

        assertEquals(now, resolution.draft().endTime());
        assertEquals(now.minusMinutes(30), resolution.draft().startTime());
    }

    @Test
    @DisplayName("past day with no time given falls back to the fixed noon default")
    void pastDayNoTime_fallsBackToNoon() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(intent("chores", -2, null, null, null, 60, null));

        assertEquals(LocalDateTime.of(2026, 8, 12, 12, 0), resolution.draft().startTime());
        assertEquals(LocalDateTime.of(2026, 8, 12, 13, 0), resolution.draft().endTime());
    }

    @Test
    @DisplayName("a stated time that would run past now is shifted back, duration preserved")
    void windowPastNow_shiftedBack_durationPreserved() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 8, 0);
        LogIntentResolver resolver = resolverAt(now);

        // "this evening" (defaultHour=19) hasn't happened yet at 08:00 -- must shift, not reject.
        var resolution = resolver.resolve(intent("gaming", 0, null, null, TimeOfDay.EVENING, 90, null));

        assertTrue(resolution.resolved());
        assertEquals(now, resolution.draft().endTime());
        assertEquals(now.minusMinutes(90), resolution.draft().startTime());
    }

    @Test
    @DisplayName("missing duration returns NEEDS clarification, not a guessed number")
    void missingDuration_notResolved() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(intent("studying", 0, null, null, TimeOfDay.MORNING, null, null));

        assertFalse(resolution.resolved());
        assertNull(resolution.draft());
        assertEquals(LogIntentResolver.Reason.MISSING_DURATION, resolution.reason());
    }

    @Test
    @DisplayName("zero or negative duration is treated as missing, not clamped")
    void nonPositiveDuration_treatedAsMissing() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(intent("studying", 0, null, null, null, 0, null));

        assertEquals(LogIntentResolver.Reason.MISSING_DURATION, resolution.reason());
    }

    @Test
    @DisplayName("duration over the session-integrity cap is refused, not clamped down to the cap")
    void durationOverCap_refused_notClamped() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(
                intent("studying", 0, null, null, null, (int) MAX_DURATION_MINUTES + 1, null));

        assertFalse(resolution.resolved());
        assertEquals(LogIntentResolver.Reason.DURATION_TOO_LONG, resolution.reason());
    }

    @Test
    @DisplayName("a future day is rejected outright, never silently reinterpreted")
    void futureDay_rejected() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(intent("studying", 1, null, null, TimeOfDay.MORNING, 30, null));

        assertFalse(resolution.resolved());
        assertEquals(LogIntentResolver.Reason.FUTURE_DAY, resolution.reason());
    }

    @Test
    @DisplayName("missing or blank activity name is rejected before any time math runs")
    void missingActivityName_rejected() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        assertEquals(LogIntentResolver.Reason.MISSING_ACTIVITY_NAME,
                resolver.resolve(intent(null, 0, null, null, TimeOfDay.MORNING, 30, null)).reason());
        assertEquals(LogIntentResolver.Reason.MISSING_ACTIVITY_NAME,
                resolver.resolve(intent("   ", 0, null, null, TimeOfDay.MORNING, 30, null)).reason());
    }

    @Test
    @DisplayName("an out-of-range hour from a hallucinating model falls through instead of throwing")
    void outOfRangeHour_fallsThroughGracefully() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 23, 0));

        var resolution = resolver.resolve(intent("studying", 0, 25, 0, TimeOfDay.MORNING, 30, null));

        assertTrue(resolution.resolved());
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), resolution.draft().startTime());
    }

    @Test
    @DisplayName("activity name is trimmed and notes pass through unchanged")
    void activityNameTrimmed_notesPassThrough() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(
                intent("  studying  ", 0, null, null, TimeOfDay.MORNING, 30, "  raw notes  "));

        assertEquals("studying", resolution.draft().activityName());
        assertEquals("  raw notes  ", resolution.draft().notes());
    }

    @Test
    @DisplayName("createdAt on a resolved draft is always null -- the server sets it, never the client")
    void createdAt_alwaysNull() {
        LogIntentResolver resolver = resolverAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        var resolution = resolver.resolve(intent("studying", 0, null, null, TimeOfDay.MORNING, 30, null));

        assertNull(resolution.draft().createdAt());
    }

    @Test
    @DisplayName("a resolved draft's times never land in the future, whatever the input")
    void resolvedDraft_neverInTheFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 6, 0);
        LogIntentResolver resolver = resolverAt(now);

        var resolution = resolver.resolve(intent("gaming", 0, null, null, TimeOfDay.NIGHT, 200, null));

        assertFalse(resolution.draft().startTime().isAfter(now));
        assertFalse(resolution.draft().endTime().isAfter(now));
    }
}
