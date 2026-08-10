package com.tracker.activity.domain;

/** One scored candidate (issue #66) — the unit an {@link ActivityNameScorer} returns per catalog entry. */
public record ActivityMatch(ActivityCandidate candidate, double score, MatchField matchedOn) {
}
