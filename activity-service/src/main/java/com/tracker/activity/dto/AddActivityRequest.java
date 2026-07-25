package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record AddActivityRequest(
        @NotBlank(message = "name feild is required")
        String name,
        @NotNull(message = "fill up the category")
        Category category,
        @Positive(message = "xpMultiplayer cannot be negative or zero")
        double xpMultiplier,
        boolean active,
        String description,
        LocalDateTime createdAt
) {
}
