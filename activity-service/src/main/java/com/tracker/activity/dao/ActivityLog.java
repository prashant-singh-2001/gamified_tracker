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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    // Session integrity (#67): quarantine state. @Builder.Default is load-bearing — without it
    // every builder call (mapToActivityLog, test fixtures) leaves this null and the
    // nullable = false column blows up at flush.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.CLEARED;
}
