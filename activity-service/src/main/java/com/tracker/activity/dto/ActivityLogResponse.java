package com.tracker.activity.dto;

import java.time.LocalDateTime;

import com.tracker.activity.dao.Activity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ActivityLogResponse(
        Long id,
        Long userId,
        Activity activity,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMinutes,
        double xpEarned,
        String notes,
        LocalDateTime createdAt,
        boolean bonusApplied,
        double bonusMultiplier,
        boolean leveledUp
) {
}
