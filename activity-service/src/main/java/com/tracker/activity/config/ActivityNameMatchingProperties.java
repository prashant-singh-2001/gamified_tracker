package com.tracker.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fuzzy activity-name resolution thresholds (issue #66). {@code autoResolveEnabled} is a kill switch
 * for the automatic substitution ONLY — ranked suggestions on a 404 are always returned, since they
 * have no side effects. Mirrors {@code session-integrity.outlier-detection-enabled}.
 */
@ConfigurationProperties(prefix = "activity-name-matching")
public record ActivityNameMatchingProperties(
        boolean autoResolveEnabled,
        // A candidate must beat this to be substituted for what the user actually typed. Deliberately
        // above DESCRIPTION_WEIGHT (0.8) in ActivityMatcher, so a description/category hit can never
        // auto-resolve — only a name hit can.
        double autoResolveThreshold,
        // If the top two candidates are within this of each other, a pick is a coin flip: fall back
        // to suggestions rather than award irreversible XP against a guess.
        double ambiguityMargin,
        double suggestionThreshold,
        int maxSuggestions) {
}
