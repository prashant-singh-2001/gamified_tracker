package com.tracker.gamification.messaging;

public record ActivityLoggedEvent(Long logId, Long userId, Long activityId, double xpEarned) {
}
