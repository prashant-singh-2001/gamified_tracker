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
        double relativeFactor,
        // Flags regardless of the user's own baseline, closing the self-consistency hole where a
        // consistently implausible user (cold-start seeding, or ratcheting up in small steps) is
        // never an outlier against their own history. Checked first, before either baseline query.
        long absoluteFlagMinutes,
        // A calendar day physically contains 1440 minutes, so a legitimate daily total across all
        // of a user's sessions cannot exceed it — closes aggregate farming via many small sessions.
        long maxDailyMinutes) {
}
