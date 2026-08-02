package com.tracker.activity.dto;

import com.tracker.activity.domain.DurationOutlierDetector;

/**
 * One row of the admin review queue (issue #67): the flagged log plus the recomputed verdict, so
 * a maintainer can see *why* it was flagged rather than a bare boolean.
 */
public record ReviewQueueItemResponse(
        ActivityLogResponse activityLog,
        double modifiedZScore,
        double median,
        int sampleSize,
        DurationOutlierDetector.Basis basis
) {
}
