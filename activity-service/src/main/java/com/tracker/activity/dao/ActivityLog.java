package com.tracker.activity.dao;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class ActivityLog {

    @Id
    @GeneratedValue
    private Long id;

    private Long userId; // from auth service

    @ManyToOne
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
