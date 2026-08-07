package com.tracker.gamification.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// #74: separate from LevelTrackerRequestDTO on purpose. That record is the shared primitive
// ActivityLoggedListener constructs directly in Java (bypassing bean validation) for
// event-driven, server-computed XP — it must stay uncapped. This one is only for the
// admin-gated manual-award door (POST /level), so it alone carries the per-call cap.
public record ManualXpAwardRequest(
        @Positive(message = "target user id cannot be negative or zero")
        Long targetUserId,
        @NotNull(message = "activity id is required")
        @Positive(message = "activity id cannot be negative or zero")
        Long activityId,
        @PositiveOrZero(message = "xp cannot be negative")
        @DecimalMax(value = "10000.0", message = "xp exceeds the per-award cap of 10000")
        double xp) {
}
