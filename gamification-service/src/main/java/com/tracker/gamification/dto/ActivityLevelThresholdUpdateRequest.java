package com.tracker.gamification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** PUT /threshold/{activityId}/{level}: the cumulative XP at which that level is reached. */
public record ActivityLevelThresholdUpdateRequest(
        @NotNull(message = "xpRequired is required")
        @Positive(message = "xpRequired must be greater than zero")
        Double xpRequired
) {
}
