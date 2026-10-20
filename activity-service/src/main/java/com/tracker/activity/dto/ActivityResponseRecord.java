package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

import java.time.LocalDateTime;

public record ActivityResponseRecord(
        // The catalog used to omit this, so clients had to infer activity ids from log history.
        Long id,
        String name,
        Category category,
        double xpMultiplier,
        boolean active,
        String description,
        LocalDateTime createdAt
) {
}
