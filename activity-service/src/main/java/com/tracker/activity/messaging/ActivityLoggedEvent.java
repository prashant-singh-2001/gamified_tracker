package com.tracker.activity.messaging;

public record ActivityLoggedEvent(Long logId, Long userId, Long activityId, double xpEarned) {
}
