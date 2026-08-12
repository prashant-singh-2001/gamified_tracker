package com.tracker.activity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hand-written Jaro-Winkler scorer (issue #66) — the only {@link ActivityNameScorer} implementation
 * today. No dependency was pulled in for this; the repo already hand-rolled a comparable algorithm
 * for {@code DurationOutlierDetector} (issue #67) rather than adding a stats library, and the same
 * reasoning applies here.
 */
public class LexicalActivityNameScorer implements ActivityNameScorer {

    // --- Jaro-Winkler constants (published algorithm; not tuning knobs) ---
    private static final double PREFIX_SCALE = 0.1;
    private static final int MAX_PREFIX = 4;
    private static final double WINKLER_BOOST_THRESHOLD = 0.7;

    // Below this, two words are unrelated rather than mistyped: Jaro-Winkler's noise floor for
    // arbitrary English words is high ("morning"/"running" scores 0.743 with nothing in common), so
    // an ungated blend would rank pure noise above a real partial match. Gating each pairwise score
    // to 0.0 makes "any non-zero candidate score" mean "something actually matched".
    private static final double TOKEN_MATCH_FLOOR = 0.82;
    // The max term identifies WHICH entry was meant; the mean term penalises query words nothing in
    // the entry explains, so a long unrelated sentence can't ride one incidental token match.
    private static final double MAX_TOKEN_WEIGHT = 0.7;
    private static final double MEAN_TOKEN_WEIGHT = 1.0 - MAX_TOKEN_WEIGHT;

    // Fields are combined by weighted MAX, not weighted sum: a strong hit on one field is evidence,
    // and averaging it against two empty fields would bury it. The weights are the confidence
    // ceiling of each field as an identifier — description/category can suggest, never identify.
    // NAME_WEIGHT stays 1.0 and DESCRIPTION_WEIGHT stays below the auto-resolve-threshold default
    // (0.86, see ActivityNameMatchingProperties) so a description hit structurally cannot
    // auto-resolve — see the caveat on this in ActivityNameScorer's Javadoc.
    private static final double NAME_WEIGHT = 1.0;
    private static final double DESCRIPTION_WEIGHT = 0.8;
    private static final double CATEGORY_WEIGHT = 0.5;

    @Override
    public List<ActivityMatch> scoreAll(String query, List<ActivityCandidate> candidates) {
        String normalizedQuery = normalize(query);
        List<String> queryTokens = tokenize(normalizedQuery);

        List<ActivityMatch> matches = new ArrayList<>(candidates.size());
        for (ActivityCandidate candidate : candidates) {
            matches.add(score(normalizedQuery, queryTokens, candidate));
        }
        return matches;
    }

    private ActivityMatch score(String normalizedQuery, List<String> queryTokens, ActivityCandidate candidate) {
        double best = NAME_WEIGHT * fieldScore(normalizedQuery, queryTokens, candidate.name());
        MatchField matchedOn = MatchField.NAME;

        double description = DESCRIPTION_WEIGHT * fieldScore(normalizedQuery, queryTokens, candidate.description());
        if (description > best) {
            best = description;
            matchedOn = MatchField.DESCRIPTION;
        }
        double category = CATEGORY_WEIGHT * fieldScore(normalizedQuery, queryTokens, candidate.category());
        if (category > best) {
            best = category;
            matchedOn = MatchField.CATEGORY;
        }
        return new ActivityMatch(candidate, best, best > 0.0 ? matchedOn : MatchField.NONE);
    }

    private double fieldScore(String normalizedQuery, List<String> queryTokens, String fieldText) {
        String normalizedField = normalize(fieldText);
        if (normalizedField.isEmpty() || normalizedQuery.isEmpty()) {
            return 0.0;
        }
        // Whole-string pass: catches typos that span the whole value ("runnning" -> "running") and
        // multi-word names where token alignment alone would under-score ("study session" -> "Study").
        double whole = gate(jaroWinkler(normalizedQuery, normalizedField));

        List<String> fieldTokens = tokenize(normalizedField);
        if (queryTokens.isEmpty() || fieldTokens.isEmpty()) {
            return whole;
        }
        double maxToken = 0.0;
        double sum = 0.0;
        for (String queryToken : queryTokens) {
            double bestForToken = 0.0;
            for (String fieldToken : fieldTokens) {
                bestForToken = Math.max(bestForToken, jaroWinkler(queryToken, fieldToken));
            }
            bestForToken = gate(bestForToken);
            maxToken = Math.max(maxToken, bestForToken);
            sum += bestForToken;
        }
        double tokenScore = MAX_TOKEN_WEIGHT * maxToken + MEAN_TOKEN_WEIGHT * (sum / queryTokens.size());
        return Math.max(whole, tokenScore);
    }

    private static double gate(double similarity) {
        return similarity >= TOKEN_MATCH_FLOOR ? similarity : 0.0;
    }

    /** Lowercase, strip every non-alphanumeric run to a single space, trim. Null-safe. */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (char ch : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                if (pendingSpace && out.length() > 0) {
                    out.append(' ');
                }
                pendingSpace = false;
                out.append(ch);
            } else {
                pendingSpace = true;
            }
        }
        return out.toString();
    }

    private static List<String> tokenize(String normalized) {
        return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
    }

    /** Jaro-Winkler: Jaro plus a bonus for a shared prefix of up to 4 chars, applied only above 0.7. */
    private static double jaroWinkler(String a, String b) {
        double jaro = jaro(a, b);
        if (jaro < WINKLER_BOOST_THRESHOLD) {
            return jaro;
        }
        int prefix = 0;
        int limit = Math.min(MAX_PREFIX, Math.min(a.length(), b.length()));
        while (prefix < limit && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        return jaro + prefix * PREFIX_SCALE * (1.0 - jaro);
    }

    /** Standard Jaro similarity: matching-character window is floor(max(len)/2) - 1. */
    private static double jaro(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        if (lenA == 0 && lenB == 0) {
            return 1.0;
        }
        if (lenA == 0 || lenB == 0) {
            return 0.0;
        }
        int window = Math.max(Math.max(lenA, lenB) / 2 - 1, 0);
        boolean[] matchedA = new boolean[lenA];
        boolean[] matchedB = new boolean[lenB];
        int matches = 0;
        for (int i = 0; i < lenA; i++) {
            int from = Math.max(0, i - window);
            int to = Math.min(lenB - 1, i + window);
            for (int j = from; j <= to; j++) {
                if (matchedB[j] || a.charAt(i) != b.charAt(j)) {
                    continue;
                }
                matchedA[i] = true;
                matchedB[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        // Transpositions: matched chars taken in order from each string, counted pairwise, halved.
        int halfTranspositions = 0;
        int k = 0;
        for (int i = 0; i < lenA; i++) {
            if (!matchedA[i]) {
                continue;
            }
            while (!matchedB[k]) {
                k++;
            }
            if (a.charAt(i) != b.charAt(k)) {
                halfTranspositions++;
            }
            k++;
        }
        double m = matches;
        return (m / lenA + m / lenB + (m - halfTranspositions / 2.0) / m) / 3.0;
    }
}
