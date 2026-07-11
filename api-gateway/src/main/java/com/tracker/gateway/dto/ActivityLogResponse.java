package com.tracker.gateway.dto;

import com.tracker.gateway.dao.Activity;

import java.time.LocalDateTime;

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
