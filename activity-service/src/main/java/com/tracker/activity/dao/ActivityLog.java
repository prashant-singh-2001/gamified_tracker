package com.tracker.activity.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue
    private Long id;

    private Long userId; // from auth service

    @ManyToOne
    private Activity activity;

    @NotNull(message = "Start Time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    private Long durationMinutes;

    // Gamification snapshot
    private double xpEarned;

    // Optional
    private String notes;

    private LocalDateTime createdAt;
}
