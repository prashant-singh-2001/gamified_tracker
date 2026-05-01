package com.tracker.activity.dao;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
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
