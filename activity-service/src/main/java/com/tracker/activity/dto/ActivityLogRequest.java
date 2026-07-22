package com.tracker.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ActivityLogRequest(
        @NotBlank(message = "Activity name is required")
        String activityName,

        @NotNull(message = "Start time is required")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        LocalDateTime endTime,

        String notes,
        LocalDateTime createdAt
) {
}
