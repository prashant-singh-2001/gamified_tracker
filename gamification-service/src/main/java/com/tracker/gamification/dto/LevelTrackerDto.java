package com.tracker.gamification.dto;

public record LevelTrackerDto(
        Long userId,
        Long activityId,
        Integer level,
        double totalXp,
        double currentLevelXp,
        boolean leveledUp
) {
}
