package com.tracker.gamification.dto;

// IDOR fix: userId removed from the client-supplied body — the caller's identity now
// comes from the trusted "userId" header the gateway injects (and activity-service
// forwards on its internal Feign call), never from the request body.
// public record LevelTrackerRequestDTO(Long userId, Long activityId, double xp) {
//     public LevelTrackerRequestDTO {
//         if (xp < 0) throw new IllegalArgumentException("xp cannot be negative");
//     }
// }

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record LevelTrackerRequestDTO(
        @NotNull(message = "actiivity id is required")
        @Positive(message = "activity id cannot be negative or zero")
        Long activityId,
        @PositiveOrZero(message = "xp cannot be negative")
        double xp) {
}
