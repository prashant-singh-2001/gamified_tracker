package com.tracker.gamification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record LevelTrackerRequestDTO(
        @NotNull(message = "user id is required")
        @Positive(message = "user id cannot be negative or zero")
        Long userId,
        @NotNull(message = "activity id is required")
        @Positive(message = "activity id cannot be negative or zero")
        Long activityId,
        @PositiveOrZero(message = "xp cannot be negative")
        double xp) {
}
