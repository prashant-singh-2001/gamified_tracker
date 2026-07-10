package com.tracker.gamification.dao;

import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@Data
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder
@Entity
@Table(name = "LevelTracker",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_level_tracker_user_activity",
                columnNames = {"user_id", "activity_id"}))
public class LevelTracker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long activityId;
    private Integer level;
    private double totalXp;
    private double currentLevelXp;
}
