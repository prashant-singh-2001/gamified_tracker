package com.tracker.gateway.dao;


import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {

    @Id
    @GeneratedValue
    public Long id;

    public String name; // Study, Gaming, Work

    @Enumerated(EnumType.STRING)
    public Category category;
    // STUDY, WORK, GAMING, CHORES

    // Gamification config
    public double xpMultiplier; // e.g. Study = 1.5, Gaming = 0.8

    public boolean active; // soft delete / enable-disable

    // Optional metadata
    public String description;

    public LocalDateTime createdAt;
}
