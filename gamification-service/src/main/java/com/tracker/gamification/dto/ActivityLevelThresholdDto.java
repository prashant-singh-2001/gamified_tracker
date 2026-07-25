package com.tracker.gamification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ActivityLevelThresholdDto(
        @NotNull(message = "activity id is required")
        @Positive(message = "activity id cannot be negative or zero")
        Long activityId,
        @NotNull(message = "level is required")
        @Positive(message = "level cannot be negative or zero")
        Integer level,
        @Positive(message = "level cannot be negative or zero")
        double xpRequired
) {
}
