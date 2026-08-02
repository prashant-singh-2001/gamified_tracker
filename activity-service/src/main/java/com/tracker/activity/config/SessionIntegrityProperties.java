package com.tracker.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Session integrity thresholds (issue #67). {@code outlierDetectionEnabled} is a kill switch for
 * layer 2 (statistical quarantine) only — the layer-1 hard cap always applies, mirroring the
 * {@code leveling.default-curve.enabled} convention in gamification-service.
 */
@ConfigurationProperties(prefix = "session-integrity")
public record SessionIntegrityProperties(
        boolean outlierDetectionEnabled,
        long maxDurationMinutes,
        double modifiedZThreshold,
        int minSamples,
        int baselineWindow,
        double relativeFactor) {
}
