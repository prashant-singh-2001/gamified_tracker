package com.tracker.activity.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /activitylog/natural} (issue #70) -- one free-text sentence. */
public record NaturalLogRequest(
        @NotBlank(message = "text is required")
        String text
) {
}
