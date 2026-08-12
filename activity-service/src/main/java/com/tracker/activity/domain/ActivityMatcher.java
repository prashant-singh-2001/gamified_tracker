package com.tracker.activity.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provider-agnostic resolution logic for a user-typed activity name (issue #66): delegates scoring
 * to an {@link ActivityNameScorer}, then applies ranking and three safety rails before offering an
 * automatic substitution. The rails — not the scoring algorithm — are the part that must survive a
 * future provider swap unchanged; see {@link ActivityNameScorer}'s Javadoc for that contract.
 */
public class ActivityMatcher {

    private final ActivityNameScorer scorer;
    private final double autoResolveThreshold;
    private final double ambiguityMargin;
    private final double suggestionThreshold;
    private final int maxSuggestions;

    public ActivityMatcher(ActivityNameScorer scorer, double autoResolveThreshold, double ambiguityMargin,
                           double suggestionThreshold, int maxSuggestions) {
        this.scorer = scorer;
        this.autoResolveThreshold = autoResolveThreshold;
        this.ambiguityMargin = ambiguityMargin;
        this.suggestionThreshold = suggestionThreshold;
        this.maxSuggestions = maxSuggestions;
    }

    public Resolution resolve(String query, List<ActivityCandidate> candidates) {
        if (query == null || query.isBlank()) {
            return new Resolution(null, List.of(), Reason.EMPTY_QUERY);
        }
        List<ActivityCandidate> safeCandidates = candidates != null ? candidates : List.of();

        List<ActivityMatch> scored = new ArrayList<>(scorer.scoreAll(query, safeCandidates));
        // Deterministic: score desc, then name asc, so an exact tie never depends on scorer/catalog
        // order — the ambiguity guard below would otherwise be non-reproducible.
        scored.sort(Comparator.comparingDouble(ActivityMatch::score).reversed()
                .thenComparing(match -> match.candidate().name()));

        List<ActivityMatch> suggestions = scored.stream()
                .filter(match -> match.score() >= suggestionThreshold)
                .limit(maxSuggestions)
                .toList();

        if (scored.isEmpty()) {
            return new Resolution(null, suggestions, Reason.NO_MATCH);
        }

        ActivityMatch top = scored.get(0);
        if (top.score() < autoResolveThreshold) {
            return new Resolution(null, suggestions,
                    suggestions.isEmpty() ? Reason.NO_MATCH : Reason.BELOW_THRESHOLD);
        }
        // Rail 1: never auto-resolve onto a soft-deleted activity (#7) — that would substitute a
        // name the user never typed and then 409 on it. The suggestion still shows, active=false.
        if (!top.candidate().active()) {
            return new Resolution(null, suggestions, Reason.INACTIVE_TOP_MATCH);
        }
        // Rail 2: ambiguity guard. A coin-flip pick is a bug, not a feature — XP is irreversible.
        if (scored.size() > 1 && top.score() - scored.get(1).score() < ambiguityMargin) {
            return new Resolution(null, suggestions, Reason.AMBIGUOUS);
        }
        return new Resolution(top, suggestions, Reason.AUTO_RESOLVED);
    }

    /**
     * {@code DISABLED} is never produced by {@link #resolve}. It exists only for
     * {@code ActivityNameResolutionService} to report when its own kill switch withheld an
     * otherwise-qualifying auto-resolve — kept on this enum rather than a second one so callers
     * have a single {@code Reason} type to switch on.
     */
    public enum Reason {
        AUTO_RESOLVED, AMBIGUOUS, BELOW_THRESHOLD, INACTIVE_TOP_MATCH, NO_MATCH, EMPTY_QUERY, DISABLED
    }

    public record Resolution(ActivityMatch autoResolved, List<ActivityMatch> suggestions, Reason reason) {
    }
}
