package com.tracker.activity.dto;

public record ActivityLevelThresholdDto(
        Long activityId,
        Integer level,
        double xpRequired
) {
}
