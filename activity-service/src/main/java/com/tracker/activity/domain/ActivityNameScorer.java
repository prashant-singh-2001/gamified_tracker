package com.tracker.activity.domain;

import java.util.List;

/**
 * Scores a user-typed activity name against the catalog (issue #66). Batch-shaped on purpose: an
 * embedding- or LLM-backed provider must be able to issue a single request for the whole catalog,
 * which a per-candidate signature would make impossible without N round trips per lookup.
 *
 * <p>Implementations return one {@link ActivityMatch} per candidate (in any order — ranking is
 * {@link ActivityMatcher}'s job), with {@code score} in {@code [0,1]}. Ranking, thresholds, and the
 * safety rails (active-only, ambiguity guard) all live in {@link ActivityMatcher}, never here, so
 * they apply uniformly no matter which scorer is wired in.
 *
 * <p><b>One guarantee does NOT come for free from a new implementation.</b> The lexical scorer's
 * field weights make it structurally impossible for a description/category-only hit to ever clear
 * the auto-resolve threshold — its weight ceiling sits below the threshold (see
 * {@link LexicalActivityNameScorer}). A calibrated semantic score has no such per-field ceiling, so
 * a future provider must re-establish that guarantee itself (e.g. by having {@link ActivityMatcher}
 * refuse to auto-resolve whenever {@code matchedOn != MatchField.NAME}) rather than assuming it
 * still holds.
 */
public interface ActivityNameScorer {

    List<ActivityMatch> scoreAll(String query, List<ActivityCandidate> candidates);
}
