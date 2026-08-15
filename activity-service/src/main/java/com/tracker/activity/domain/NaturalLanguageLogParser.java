package com.tracker.activity.domain;

import java.util.Optional;

/**
 * Turns one free-text sentence into a {@link ParsedLogIntent} (issue #70). Mirrors
 * {@link WeeklyDigestNarrator} (#65) and {@link ActivityNameScorer} (#66): the backend behind an
 * implementation is a runtime/config concern, never a caller concern.
 */
public interface NaturalLanguageLogParser {

    /**
     * Return the parsed intent, or {@link Optional#empty()} if none could be produced. Implementations
     * are not required to catch every exception themselves -- the orchestrating service wraps this
     * call so a model outage degrades the endpoint to "couldn't parse that, try again," never a 500.
     * The returned intent is raw, unvalidated model output; {@link LogIntentResolver} is what checks
     * and clamps it, never this.
     */
    Optional<ParsedLogIntent> parse(String text);
}
