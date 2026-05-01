package com.tracker.gateway.dto;

import com.tracker.gateway.dao.Activity;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogResponse {
    private Long id;

    private Long userId; // from auth service

    private Activity activity;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Long durationMinutes;

    // Gamification snapshot
    private double xpEarned;

    // Optional
    private String notes;

    private LocalDateTime createdAt;
}
