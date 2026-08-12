package com.tracker.activity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the ranking + safety rails against a stub {@link ActivityNameScorer} with hand-written
 * scores — no Jaro-Winkler arithmetic here (that's {@link LexicalActivityNameScorerTest}'s job), so
 * these cases stay valid verbatim if a semantic provider is ever wired in behind the same interface.
 */
@DisplayName("ActivityMatcher (issue #66)")
class ActivityMatcherTest {

    // Documented defaults (see activity-name-matching.* in application.yaml).
    private static final double AUTO_RESOLVE_THRESHOLD = 0.86;
    private static final double AMBIGUITY_MARGIN = 0.05;
    private static final double SUGGESTION_THRESHOLD = 0.45;
    private static final int MAX_SUGGESTIONS = 3;

    @Test
    @DisplayName("a single candidate at or above the threshold auto-resolves")
    void aboveThreshold_autoResolves() {
        ActivityMatcher matcher = matcherWith(match("Running", true, 0.90, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve("query", List.of(candidate("Running", true)));

        assertEquals(ActivityMatcher.Reason.AUTO_RESOLVED, resolution.reason());
        assertEquals("Running", resolution.autoResolved().candidate().name());
        assertEquals(0.90, resolution.autoResolved().score(), 1e-9);
    }

    @Test
    @DisplayName("a single candidate below the threshold but above the suggestion floor is suggested, not resolved")
    void belowThreshold_isSuggestedNotResolved() {
        ActivityMatcher matcher = matcherWith(match("Running", true, 0.70, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve("query", List.of(candidate("Running", true)));

        assertEquals(ActivityMatcher.Reason.BELOW_THRESHOLD, resolution.reason());
        assertNull(resolution.autoResolved());
        assertEquals(1, resolution.suggestions().size());
        assertEquals("Running", resolution.suggestions().get(0).candidate().name());
    }

    @Test
    @DisplayName("a 0.03 gap between the top two candidates is ambiguous -- no auto-resolve")
    void topTwoWithinMargin_isAmbiguous() {
        ActivityMatcher matcher = matcherWith(
                match("Studying", true, 0.90, MatchField.NAME),
                match("Study", true, 0.87, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve(
                "query", List.of(candidate("Studying", true), candidate("Study", true)));

        assertEquals(ActivityMatcher.Reason.AMBIGUOUS, resolution.reason());
        assertNull(resolution.autoResolved());
        assertEquals(2, resolution.suggestions().size());
    }

    @Test
    @DisplayName("a 0.06 gap between the top two candidates clears the ambiguity guard -- auto-resolves")
    void topTwoBeyondMargin_resolves() {
        ActivityMatcher matcher = matcherWith(
                match("Studying", true, 0.90, MatchField.NAME),
                match("Study", true, 0.84, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve(
                "query", List.of(candidate("Studying", true), candidate("Study", true)));

        assertEquals(ActivityMatcher.Reason.AUTO_RESOLVED, resolution.reason());
        assertEquals("Studying", resolution.autoResolved().candidate().name());
    }

    @Test
    @DisplayName("an inactive top match is never auto-resolved onto, even above the threshold -- it only suggests")
    void inactiveTopMatch_neverAutoResolves() {
        ActivityMatcher matcher = matcherWith(match("Running", false, 0.95, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve("query", List.of(candidate("Running", false)));

        assertEquals(ActivityMatcher.Reason.INACTIVE_TOP_MATCH, resolution.reason());
        assertNull(resolution.autoResolved());
        assertEquals(1, resolution.suggestions().size());
        assertFalse(resolution.suggestions().get(0).candidate().active());
    }

    @Test
    @DisplayName("suggestions are capped at maxSuggestions even when more candidates clear the floor")
    void suggestions_areCappedAtMaxSuggestions() {
        ActivityMatcher matcher = matcherWith(
                match("A", true, 0.80, MatchField.NAME),
                match("B", true, 0.75, MatchField.NAME),
                match("C", true, 0.70, MatchField.NAME),
                match("D", true, 0.65, MatchField.NAME),
                match("E", true, 0.60, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve("query", List.of(
                candidate("A", true), candidate("B", true), candidate("C", true),
                candidate("D", true), candidate("E", true)));

        assertEquals(MAX_SUGGESTIONS, resolution.suggestions().size());
        assertEquals("A", resolution.suggestions().get(0).candidate().name());
        assertEquals("C", resolution.suggestions().get(2).candidate().name());
    }

    @Test
    @DisplayName("candidates with equal scores are tie-broken by name ascending, not catalog order")
    void equalScores_tieBreakByNameAscending() {
        ActivityMatcher matcher = matcherWith(
                match("Beta", true, 0.50, MatchField.NAME),
                match("Alpha", true, 0.50, MatchField.NAME));

        ActivityMatcher.Resolution resolution = matcher.resolve(
                "query", List.of(candidate("Beta", true), candidate("Alpha", true)));

        assertEquals("Alpha", resolution.suggestions().get(0).candidate().name());
        assertEquals("Beta", resolution.suggestions().get(1).candidate().name());
    }

    @Test
    @DisplayName("an empty catalog resolves to NO_MATCH with no suggestions")
    void emptyCatalog_isNoMatch() {
        ActivityMatcher matcher = matcherWith();

        ActivityMatcher.Resolution resolution = matcher.resolve("query", List.of());

        assertEquals(ActivityMatcher.Reason.NO_MATCH, resolution.reason());
        assertTrue(resolution.suggestions().isEmpty());
    }

    @Test
    @DisplayName("a blank query is EMPTY_QUERY and never reaches the scorer")
    void blankQuery_neverReachesScorer() {
        StubScorer stub = new StubScorer(List.of());
        ActivityMatcher matcher = new ActivityMatcher(
                stub, AUTO_RESOLVE_THRESHOLD, AMBIGUITY_MARGIN, SUGGESTION_THRESHOLD, MAX_SUGGESTIONS);

        ActivityMatcher.Resolution resolution = matcher.resolve("   ", List.of(candidate("Running", true)));

        assertEquals(ActivityMatcher.Reason.EMPTY_QUERY, resolution.reason());
        assertTrue(resolution.suggestions().isEmpty());
        assertFalse(stub.wasCalled());
    }

    private static ActivityMatcher matcherWith(ActivityMatch... fixedMatches) {
        return new ActivityMatcher(new StubScorer(List.of(fixedMatches)),
                AUTO_RESOLVE_THRESHOLD, AMBIGUITY_MARGIN, SUGGESTION_THRESHOLD, MAX_SUGGESTIONS);
    }

    private static ActivityMatch match(String name, boolean active, double score, MatchField matchedOn) {
        return new ActivityMatch(candidate(name, active), score, matchedOn);
    }

    private static ActivityCandidate candidate(String name, boolean active) {
        return new ActivityCandidate(name, null, null, active);
    }

    /** Returns a fixed set of matches regardless of query/candidates, and records whether it ran. */
    private static final class StubScorer implements ActivityNameScorer {
        private final List<ActivityMatch> fixedMatches;
        private boolean called = false;

        StubScorer(List<ActivityMatch> fixedMatches) {
            this.fixedMatches = fixedMatches;
        }

        @Override
        public List<ActivityMatch> scoreAll(String query, List<ActivityCandidate> candidates) {
            called = true;
            return fixedMatches;
        }

        boolean wasCalled() {
            return called;
        }
    }
}
