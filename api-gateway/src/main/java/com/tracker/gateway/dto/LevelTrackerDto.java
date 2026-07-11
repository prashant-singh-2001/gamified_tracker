package com.tracker.gateway.dto;

public record LevelTrackerDto(
        Long userId,
        Long activityId,
        Integer level,
        double totalXp,
        double currentLevelXp,
        boolean leveledUp
) {
}
