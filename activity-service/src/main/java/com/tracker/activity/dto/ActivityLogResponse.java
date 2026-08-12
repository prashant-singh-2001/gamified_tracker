package com.tracker.activity.dto;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ActivityLogResponse(
        Long id,
        Long userId,
        Activity activity,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMinutes,
        double xpEarned,
        String notes,
        LocalDateTime createdAt,
        boolean bonusApplied,
        double bonusMultiplier,
        boolean leveledUp,
        int currentStreak,
        double streakMultiplier,
        // Session integrity (#67): silently withholding XP with no signal is worse than the
        // problem being solved — FLAGGED means XP is pending maintainer review.
        ReviewStatus reviewStatus,
        // Issue #66: non-null ONLY when activityName did not match exactly and was fuzzy-resolved.
        // A silent substitution of one XP-bearing activity for another is worse than the 404 it
        // replaces -- the client must be able to see and correct it.
        ActivityNameResolution nameResolution
) {
}
