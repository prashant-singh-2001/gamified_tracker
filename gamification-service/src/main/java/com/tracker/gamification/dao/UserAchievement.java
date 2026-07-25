package com.tracker.gamification.dao;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_achievement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_achievement",
                columnNames = {"user_id", "achievement_id"}))
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long achievementId;

    private LocalDateTime unlockedAt;

}
