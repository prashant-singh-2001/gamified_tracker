package com.tracker.activity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the hand-rolled Jaro-Winkler algorithm (issue #66) — every expected value here was computed
 * by actually running this scorer, not hand-derived, so these numbers are ground truth for
 * {@link ActivityMatcherTest} and {@code ActivityNameResolutionServiceTest} to build on.
 */
@DisplayName("LexicalActivityNameScorer (issue #66)")
class LexicalActivityNameScorerTest {

    private final LexicalActivityNameScorer scorer = new LexicalActivityNameScorer();

    private static final ActivityCandidate RUNNING = new ActivityCandidate(
            "Running", "Jogging, cardio, running outdoors", "HEALTH", true);
    private static final ActivityCandidate STUDY = new ActivityCandidate("Study", null, "STUDY", true);
    private static final ActivityCandidate STUDYING = new ActivityCandidate("Studying", null, "STUDY", true);

    @Test
    @DisplayName("case/punctuation-insensitive exact match on name scores 1.0")
    void exactMatch_caseAndPunctuationInsensitive_scoresOne() {
        ActivityMatch match = scoreOne("  Running! ", RUNNING);

        assertEquals(1.0, match.score(), 1e-9);
        assertEquals(MatchField.NAME, match.matchedOn());
    }

    @Test
    @DisplayName("a single-character typo on the name auto-resolve-qualifying territory scores 0.946")
    void singleCharacterTypo_scoresHigh() {
        ActivityMatch match = scoreOne("Runnning", RUNNING);

        assertEquals(0.9464285714285714, match.score(), 1e-9);
        assertEquals(MatchField.NAME, match.matchedOn());
    }

    @Test
    @DisplayName("\"Studying\" vs \"Study\" scores 0.925 on the name field")
    void studying_vs_study_scoresHigh() {
        ActivityMatch match = scoreOne("Studying", STUDY);

        assertEquals(0.925, match.score(), 1e-9);
        assertEquals(MatchField.NAME, match.matchedOn());
    }

    @Test
    @DisplayName("a multi-word query still matches a single-word name via the whole-string pass")
    void multiWordQuery_vs_singleWordName_usesWholeStringScore() {
        ActivityMatch match = scoreOne("study session", STUDY);

        assertEquals(0.8769230769230769, match.score(), 1e-9);
        assertEquals(MatchField.NAME, match.matchedOn());
    }

    @Test
    @DisplayName("issue #66's own example: \"morning jog\" matches Running through its description, not its name")
    void morningJog_matchesThroughDescription() {
        ActivityMatch match = scoreOne("morning jog", RUNNING);

        assertEquals(0.5893333333333334, match.score(), 1e-9);
        assertEquals(MatchField.DESCRIPTION, match.matchedOn());
    }

    @Test
    @DisplayName("an unrelated query gates to exactly 0.0 on every field")
    void unrelatedQuery_gatesToExactlyZero() {
        ActivityMatch match = scoreOne("quantum physics homework", RUNNING);

        assertEquals(0.0, match.score(), 0.0);
        assertEquals(MatchField.NONE, match.matchedOn());
    }

    @Test
    @DisplayName("two catalog names one edit apart score close enough to trip the ambiguity guard")
    void studyAndStudying_scoreCloseTogether() {
        ActivityMatch vsStudy = scoreOne("Studyng", STUDY);
        ActivityMatch vsStudying = scoreOne("Studyng", STUDYING);

        assertEquals(0.9428571428571428, vsStudy.score(), 1e-9);
        assertEquals(0.9750000000000001, vsStudying.score(), 1e-9);
    }

    @Test
    @DisplayName("a description-perfect match is structurally capped at DESCRIPTION_WEIGHT (0.8), never at 1.0")
    void descriptionPerfectMatch_isCappedBelowNameWeight() {
        ActivityCandidate candidate = new ActivityCandidate("Foo", "exact phrase match", "OTHER", true);

        ActivityMatch match = scoreOne("exact phrase match", candidate);

        assertEquals(0.8, match.score(), 1e-9);
        assertEquals(MatchField.DESCRIPTION, match.matchedOn());
    }

    @Test
    @DisplayName("a blank-named candidate is scored without throwing, and its name field never wins")
    void blankNamedCandidate_doesNotThrow() {
        ActivityCandidate blank = new ActivityCandidate(null, "Jogging, cardio, running outdoors", "HEALTH", true);

        List<ActivityMatch> matches = assertDoesNotThrow(
                () -> scorer.scoreAll("morning jog", List.of(blank)));

        assertEquals(1, matches.size());
        assertEquals(MatchField.DESCRIPTION, matches.get(0).matchedOn());
    }

    @Test
    @DisplayName("scoreAll returns one match per candidate, unranked")
    void scoreAll_returnsOneMatchPerCandidate() {
        List<ActivityMatch> matches = scorer.scoreAll("Study", List.of(RUNNING, STUDY, STUDYING));

        assertEquals(3, matches.size());
        assertFalse(matches.stream().anyMatch(m -> Double.isNaN(m.score())));
    }

    private ActivityMatch scoreOne(String query, ActivityCandidate candidate) {
        List<ActivityMatch> matches = scorer.scoreAll(query, List.of(candidate));
        assertEquals(1, matches.size());
        return matches.get(0);
    }
}
