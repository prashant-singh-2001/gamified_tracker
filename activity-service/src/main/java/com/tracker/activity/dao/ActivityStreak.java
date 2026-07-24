package com.tracker.activity.dao;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "activity_streak",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_activity_streak_user_activity",
                columnNames = {"user_id", "activity_id"}))
public class ActivityStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long activityId;

    private int currentStreak;
    private int longestStreak;

    private LocalDate lastActivityDate;

}
