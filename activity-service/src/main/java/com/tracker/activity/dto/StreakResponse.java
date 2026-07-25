package com.tracker.activity.dto;

import java.time.LocalDate;

public record StreakResponse(Long activityId, int currentStreak, int longestStreak, LocalDate lastActivityDate) {
}
