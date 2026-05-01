package com.tracker.gateway.dto;

import com.tracker.gateway.dao.Category;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddActivityRequest {

    private String name; // Study, Gaming, Work

    @Enumerated(EnumType.STRING)
    private Category category;
    // STUDY, WORK, GAMING, CHORES

    // Gamification config
    private double xpMultiplier; // e.g. Study = 1.5, Gaming = 0.8

    private boolean active; // soft delete / enable-disable

    // Optional metadata
    private String description;

    private LocalDateTime createdAt;
}
