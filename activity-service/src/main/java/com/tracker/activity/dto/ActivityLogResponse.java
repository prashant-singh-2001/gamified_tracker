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
        ReviewStatus reviewStatus
) {
}
