package com.tracker.activity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DurationOutlierDetector (issue #67, layer 2)")
class DurationOutlierDetectorTest {

    // modifiedZThreshold=3.5 (Iglewicz-Hoaglin default), minSamples=5, relativeFactor=3.0
    private final DurationOutlierDetector detector = new DurationOutlierDetector(3.5, 5, 3.0);

    // priors: 10,20,...,100 -> median=55, MAD=25 (hand-computed, see class Javadoc-free comment below)
    private static final List<Long> SPREAD_PRIORS =
            List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);

    @Test
    @DisplayName("fewer than min-samples priors: abstains regardless of candidate size")
    void belowMinSamples_abstains() {
        List<Long> priors = List.of(10L, 20L, 30L); // 3 < minSamples(5)

        var verdict = detector.evaluate(1_000_000L, priors);

        assertFalse(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.INSUFFICIENT_SAMPLES, verdict.basis());
        assertEquals(3, verdict.sampleSize());
    }

    @Test
    @DisplayName("one-sided: a short session is never flagged, however extreme")
    void shortSession_neverFlagged() {
        // median of SPREAD_PRIORS is 55; candidate well below it
        var verdict = detector.evaluate(1L, SPREAD_PRIORS);

        assertFalse(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.MODIFIED_Z_SCORE, verdict.basis());
    }

    @Test
    @DisplayName("known median/MAD vector: just under the 3.5 threshold is not flagged")
    void modifiedZScore_justUnderThreshold_notFlagged() {
        // median=55, MAD=25 -> z(184) = 0.6745*(184-55)/25 ≈ 3.4804
        var verdict = detector.evaluate(184L, SPREAD_PRIORS);

        assertFalse(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.MODIFIED_Z_SCORE, verdict.basis());
        assertEquals(55.0, verdict.median(), 1e-9);
        assertEquals(3.4804, verdict.modifiedZScore(), 1e-3);
    }

    @Test
    @DisplayName("known median/MAD vector: just over the 3.5 threshold is flagged")
    void modifiedZScore_justOverThreshold_isFlagged() {
        // median=55, MAD=25 -> z(185) = 0.6745*(185-55)/25 ≈ 3.5074
        var verdict = detector.evaluate(185L, SPREAD_PRIORS);

        assertTrue(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.MODIFIED_Z_SCORE, verdict.basis());
        assertEquals(3.5074, verdict.modifiedZScore(), 1e-3);
    }

    @Test
    @DisplayName("MAD == 0 (a near-constant user): falls back to mean absolute deviation")
    void madIsZero_fallsBackToMeanAbsoluteDeviation() {
        // nine 60s and one 70: median=60, MAD=0, meanAD=1.0
        List<Long> priors = List.of(60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 70L);

        var belowThreshold = detector.evaluate(61L, priors); // z ≈ 0.798
        var aboveThreshold = detector.evaluate(100L, priors); // z ≈ 31.9

        assertFalse(belowThreshold.flagged());
        assertEquals(DurationOutlierDetector.Basis.MEAN_AD_FALLBACK, belowThreshold.basis());

        assertTrue(aboveThreshold.flagged());
        assertEquals(DurationOutlierDetector.Basis.MEAN_AD_FALLBACK, aboveThreshold.basis());
    }

    @Test
    @DisplayName("every prior identical (zero dispersion by any measure): falls back to a relative multiple of the median")
    void allIdenticalPriors_fallsBackToRelativeMultiple() {
        List<Long> priors = List.of(60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L, 60L);
        // relativeFactor=3.0 -> threshold is median*3 = 180

        var atThreshold = detector.evaluate(180L, priors);   // not > 180
        var overThreshold = detector.evaluate(181L, priors); // > 180

        assertFalse(atThreshold.flagged());
        assertEquals(DurationOutlierDetector.Basis.RELATIVE_FALLBACK, atThreshold.basis());

        assertTrue(overThreshold.flagged());
        assertEquals(DurationOutlierDetector.Basis.RELATIVE_FALLBACK, overThreshold.basis());
    }

    @Test
    @DisplayName("median of zero (all-zero priors) never flags via the relative fallback, avoiding a zero threshold")
    void zeroMedian_relativeFallbackNeverFlags() {
        List<Long> priors = List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        var verdict = detector.evaluate(1000L, priors);

        assertFalse(verdict.flagged());
        assertEquals(DurationOutlierDetector.Basis.RELATIVE_FALLBACK, verdict.basis());
    }

    @Test
    @DisplayName("a candidate exactly at the median is never flagged (one-sided: only strictly greater counts)")
    void candidateEqualsMedian_notFlagged() {
        var verdict = detector.evaluate(55L, SPREAD_PRIORS);

        assertFalse(verdict.flagged());
    }
}
