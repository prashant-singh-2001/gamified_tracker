package com.tracker.activity.dto;

import com.tracker.activity.dao.ActivityStreak;

import java.time.LocalDate;

/**
 * @param active whether the streak is still alive: the last activity was today or yesterday (UTC).
 *               The stored {@code currentStreak} is only rewritten when the next log arrives, so after a
 *               gap it keeps its old value; {@code active} is what tells a client that value is stale.
 *               Today counts as well as yesterday because the streak is extended by logging today, so a
 *               streak whose last day was yesterday is still saveable.
 */
public record StreakResponse(Long activityId, int currentStreak, int longestStreak, LocalDate lastActivityDate,
                             boolean active) {

    public static StreakResponse from(ActivityStreak streak, LocalDate todayUtc) {
        return new StreakResponse(streak.getActivityId(), streak.getCurrentStreak(), streak.getLongestStreak(),
                streak.getLastActivityDate(), isActive(streak.getLastActivityDate(), todayUtc));
    }

    static boolean isActive(LocalDate lastActivityDate, LocalDate todayUtc) {
        return lastActivityDate != null && !lastActivityDate.isBefore(todayUtc.minusDays(1));
    }
}
