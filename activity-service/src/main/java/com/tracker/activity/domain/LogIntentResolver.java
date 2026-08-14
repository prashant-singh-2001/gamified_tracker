package com.tracker.activity.domain;

import com.tracker.activity.dto.ActivityLogRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Turns a {@link ParsedLogIntent} into a concrete, committable {@link ActivityLogRequest} (issue
 * #70) -- pure and deterministic, no Spring, no model call. This is the class that actually decides
 * what "this morning" means; the model only classified it. Mirrors {@link ActivityMatcher}'s split:
 * AI/fuzzy output feeds in, a plain Java class applies every safety rail before anything downstream
 * can trust the result.
 *
 * <p>The one invariant every branch below must preserve: the produced {@code startTime}/{@code
 * endTime} can never land in the future, so a resolved draft is always valid input to the existing
 * {@code @PastOrPresent} constraints on {@link ActivityLogRequest} without the caller re-checking.
 */
public class LogIntentResolver {

    // A past day named with no time at all ("yesterday, 30 minutes") has no honest "just finished"
    // anchor -- noon is a fixed, documented, deterministic default rather than a guess dressed up
    // as one.
    private static final LocalTime NO_TIME_GIVEN_DEFAULT = LocalTime.NOON;

    private final Clock clock;
    private final long maxDurationMinutes;

    public LogIntentResolver(Clock clock, long maxDurationMinutes) {
        this.clock = clock;
        this.maxDurationMinutes = maxDurationMinutes;
    }

    public Resolution resolve(ParsedLogIntent intent) {
        if (intent == null || intent.activityName() == null || intent.activityName().isBlank()) {
            return new Resolution(null, Reason.MISSING_ACTIVITY_NAME);
        }
        if (intent.dayOffset() > 0) {
            return new Resolution(null, Reason.FUTURE_DAY);
        }
        // Zero/negative folds into "missing" -- neither is a duration a resolver should try to
        // repair, and a dedicated Reason for a value that should never occur from real model output
        // would just be dead code to maintain.
        Integer duration = intent.durationMinutes();
        if (duration == null || duration <= 0) {
            return new Resolution(null, Reason.MISSING_DURATION);
        }
        if (duration > maxDurationMinutes) {
            return new Resolution(null, Reason.DURATION_TOO_LONG);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startTime;
        LocalDateTime endTime;

        if (intent.dayOffset() == 0 && intent.startHour() == null && intent.timeOfDay() == null) {
            // No day, no time at all: "studied for 30 minutes." The only honest anchor is "just
            // finished" -- anything else invents a clock time the user never gave.
            endTime = now;
            startTime = endTime.minusMinutes(duration);
        } else {
            LocalDate anchorDate = now.toLocalDate().plusDays(intent.dayOffset());
            startTime = LocalDateTime.of(anchorDate, resolveAnchorTime(intent));
            endTime = startTime.plusMinutes(duration);

            // A stated time can still land in the future relative to right now (e.g. "this evening"
            // while it's still morning, or the duration running past the current moment) -- shift
            // the whole window back so it ends now, keeping the stated duration intact rather than
            // silently discarding what the user actually said.
            if (endTime.isAfter(now)) {
                endTime = now;
                startTime = endTime.minusMinutes(duration);
            }
        }

        ActivityLogRequest draft = new ActivityLogRequest(
                intent.activityName().trim(), startTime, endTime, intent.notes(), null);
        return new Resolution(draft, Reason.RESOLVED);
    }

    /** Never throws: an out-of-range hour/minute from a hallucinating model falls through to the
     * next-best anchor instead of blowing up a request that a clarification response could have
     * handled cleanly. */
    private LocalTime resolveAnchorTime(ParsedLogIntent intent) {
        Integer hour = intent.startHour();
        if (hour != null && hour >= 0 && hour <= 23) {
            Integer minute = intent.startMinute();
            int safeMinute = (minute != null && minute >= 0 && minute <= 59) ? minute : 0;
            return LocalTime.of(hour, safeMinute);
        }
        if (intent.timeOfDay() != null) {
            return LocalTime.of(intent.timeOfDay().defaultHour(), 0);
        }
        return NO_TIME_GIVEN_DEFAULT;
    }

    public record Resolution(ActivityLogRequest draft, Reason reason) {
        public boolean resolved() {
            return draft != null;
        }
    }

    public enum Reason {
        RESOLVED, MISSING_ACTIVITY_NAME, MISSING_DURATION, DURATION_TOO_LONG, FUTURE_DAY
    }
}
