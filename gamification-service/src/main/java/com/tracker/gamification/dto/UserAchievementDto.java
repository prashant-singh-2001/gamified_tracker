package com.tracker.gamification.dto;

import java.time.LocalDateTime;

/**
 * A badge the caller has unlocked — the {@code achievement} definition joined with the
 * {@code user_achievement} row's unlock time, so a client renders a trophy case from one call.
 */
public record UserAchievementDto(
        Long achievementId,
        String code,
        String name,
        String description,
        String criteriaType,
        Long threshold,
        Long activityId,
        LocalDateTime unlockedAt
) {
}
