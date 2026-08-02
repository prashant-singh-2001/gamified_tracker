package com.tracker.activity.domain;

import java.util.List;

/**
 * One-sided modified z-score (Iglewicz-Hoaglin) outlier detector for activity-log durations
 * (issue #67). Pure domain logic, no Spring/JPA dependency, so it is exhaustively unit-testable.
 */
public class DurationOutlierDetector {

    private static final double MAD_TO_SIGMA = 0.6745;
    private static final double MEAN_AD_TO_SIGMA = 1.253314;

    private final double modifiedZThreshold;
    private final int minSamples;
    private final double relativeFactor;

    public DurationOutlierDetector(double modifiedZThreshold, int minSamples, double relativeFactor) {
        this.modifiedZThreshold = modifiedZThreshold;
        this.minSamples = minSamples;
        this.relativeFactor = relativeFactor;
    }

    public Verdict evaluate(long candidateDuration, List<Long> priorDurations) {
        int sampleSize = priorDurations.size();
        if (sampleSize < minSamples) {
            return new Verdict(false, 0.0, 0.0, sampleSize, Basis.INSUFFICIENT_SAMPLES);
        }

        double median = median(priorDurations);

        // One-sided: a short session is never a threat, so only candidates above the median
        // are even considered. This roughly halves the false-positive rate.
        if (candidateDuration <= median) {
            return new Verdict(false, 0.0, median, sampleSize, Basis.MODIFIED_Z_SCORE);
        }

        double mad = medianAbsoluteDeviation(priorDurations, median);
        if (mad > 0) {
            double z = MAD_TO_SIGMA * (candidateDuration - median) / mad;
            return new Verdict(z > modifiedZThreshold, z, median, sampleSize, Basis.MODIFIED_Z_SCORE);
        }

        // MAD == 0 is common, not exotic (e.g. a user who always logs exactly 60 minutes) —
        // fall back to the mean absolute deviation, as the original Iglewicz-Hoaglin paper prescribes.
        double meanAd = meanAbsoluteDeviation(priorDurations, median);
        if (meanAd > 0) {
            double z = (candidateDuration - median) / (MEAN_AD_TO_SIGMA * meanAd);
            return new Verdict(z > modifiedZThreshold, z, median, sampleSize, Basis.MEAN_AD_FALLBACK);
        }

        // Every prior sample is identical: zero dispersion by either measure. Fall back to a
        // simple relative multiple of the median so a single wild outlier can still be caught.
        boolean flagged = median > 0 && candidateDuration > median * relativeFactor;
        return new Verdict(flagged, 0.0, median, sampleSize, Basis.RELATIVE_FALLBACK);
    }

    private double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private double medianAbsoluteDeviation(List<Long> values, double median) {
        List<Double> deviations = values.stream().map(v -> Math.abs(v - median)).sorted().toList();
        int n = deviations.size();
        if (n % 2 == 1) {
            return deviations.get(n / 2);
        }
        return (deviations.get(n / 2 - 1) + deviations.get(n / 2)) / 2.0;
    }

    private double meanAbsoluteDeviation(List<Long> values, double median) {
        return values.stream().mapToDouble(v -> Math.abs(v - median)).average().orElse(0.0);
    }

    public enum Basis {
        INSUFFICIENT_SAMPLES,
        MODIFIED_Z_SCORE,
        MEAN_AD_FALLBACK,
        RELATIVE_FALLBACK
    }

    public record Verdict(boolean flagged, double modifiedZScore, double median, int sampleSize, Basis basis) {
    }
}
