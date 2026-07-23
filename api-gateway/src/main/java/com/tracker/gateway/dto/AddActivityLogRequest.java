package com.tracker.gateway.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record AddActivityLogRequest(
        @NotNull(message = "user id is required")
        @Positive(message = "user id cannot be negative or zero")
        Long userId,
        @NotBlank(message = "activity name is required")
        String activityName,
        @FutureOrPresent(message = "start Time should be future")
        LocalDateTime startTime,
        @Future(message = "end time should be future")
        LocalDateTime endTime,
        String notes,
        LocalDateTime createdAt
) {
}
