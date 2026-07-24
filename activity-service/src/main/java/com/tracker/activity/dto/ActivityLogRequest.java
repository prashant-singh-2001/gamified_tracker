package com.tracker.activity.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ActivityLogRequest(
        @NotBlank(message = "Activity name is required")
        String activityName,

        @NotNull(message = "Start time is required")
        @FutureOrPresent(message = "start Time should be future")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        @Future(message = "end time should be future")
        LocalDateTime endTime,

        String notes,
        LocalDateTime createdAt
) {
}
