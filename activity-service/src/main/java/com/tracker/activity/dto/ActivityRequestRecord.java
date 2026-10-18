package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record ActivityRequestRecord(
        @NotBlank(message = "name feild is required")
        String name,
        @NotNull(message = "fill up the category")
        Category category,
        @Positive(message = "xpMultiplayer cannot be negative or zero")
        double xpMultiplier,
        // #84: boxed, not primitive -- omitting this field must be distinguishable from an
        // explicit false, so the service layer can default a missing value to "active" instead
        // of silently creating an invisible, unrecoverable activity (there is no update/delete
        // endpoint for activities at all).
        Boolean active,
        String description,
        LocalDateTime createdAt
) {
}