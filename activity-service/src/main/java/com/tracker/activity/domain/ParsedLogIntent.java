package com.tracker.activity.domain;

/**
 * Exactly what a {@link NaturalLanguageLogParser} may return (issue #70). Deliberately has NO
 * timestamp field of any kind -- an LLM is unreliable at calendar arithmetic, so it is never asked
 * to do any. {@link LogIntentResolver} is the only thing that turns this into concrete times,
 * against a real {@link java.time.Clock}, in plain deterministic Java -- the model classifies, it
 * never computes (the same split issue #65's narrator uses for numbers).
 *
 * <p>Every field here is raw, unvalidated model output. {@code activityName} and {@code notes} are
 * derived from the user's own free text and must be treated as untrusted, same as
 * {@code DigestFacts.noteLines()} (#65) -- {@link LogIntentResolver} and, on commit, the existing
 * {@code ActivityLogRequest} validation are what make this record safe to act on.
 */
public record ParsedLogIntent(
        String activityName,
        // 0 = today, negative = that many days ago. A positive value means the model read "future"
        // language into the text; LogIntentResolver rejects it outright -- you record what you did.
        int dayOffset,
        // An explicit clock time the text named ("at 7am"). Wins over timeOfDay when present.
        Integer startHour,
        Integer startMinute,
        // A coarse bucket ("this morning", "last night"). Used only when startHour is absent.
        TimeOfDay timeOfDay,
        // Minutes spent, if the text stated a duration. Null is NEVER defaulted to a number --
        // a guessed duration is guessed XP, so LogIntentResolver requires this to be present.
        Integer durationMinutes,
        // Free-text context beyond the activity name itself (e.g. "Spring Boot" from "studied
        // Spring Boot"). May be null or blank.
        String notes) {
}
