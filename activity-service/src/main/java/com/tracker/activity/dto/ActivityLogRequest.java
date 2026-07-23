package com.tracker.activity.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record ActivityLogRequest(
        @NotNull(message = "user id is required")
        @Positive(message = "user id cannot be negative or zero")
        Long userId,
        @NotBlank(message = "activity name is required")
        String activityName,
        @FutureOrPresent
        LocalDateTime startTime,
        @Future
        LocalDateTime endTime,
        String notes,
        LocalDateTime createdAt
) {
}
