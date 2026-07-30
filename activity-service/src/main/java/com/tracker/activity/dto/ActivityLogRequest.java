package com.tracker.activity.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record ActivityLogRequest(
        @NotBlank(message = "Activity name is required")
        String activityName,

        @NotNull(message = "Start time is required")
        @PastOrPresent(message = "start Time should be past or present")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        LocalDateTime endTime,

        String notes,
        LocalDateTime createdAt
) {
}
