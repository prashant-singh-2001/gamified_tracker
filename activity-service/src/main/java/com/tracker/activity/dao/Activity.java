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
        name = "activity",
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

    /**
     * The XP multiplier that actually applies to this activity: the per-activity override when set
     * ({@code xpMultiplier > 0}), otherwise the {@link Category} default. A non-positive stored
     * value — including the {@code 0.0} a client gets by omitting the field — means "no override,
     * use the category base". Falls back to {@code OTHER} (1.0) if the category is somehow null.
     *
     * <p>Named {@code effectiveXpMultiplier()} rather than {@code getEffectiveXpMultiplier()} on
     * purpose, so Jackson does not serialize it into the {@code Activity} JSON embedded in an
     * {@code ActivityLogResponse}.
     */
    public double effectiveXpMultiplier() {
        if (xpMultiplier > 0) {
            return xpMultiplier;
        }
        return category != null ? category.baseXpMultiplier() : Category.OTHER.baseXpMultiplier();
    }
}
