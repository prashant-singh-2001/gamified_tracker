package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

import java.time.LocalDateTime;

/**
 * One quick-pick: an activity the caller logs often.
 *
 * @param timesLogged how many logs the caller has for it (every review status: a flagged session is
 *                    still something they chose to log)
 * @param lastUsedAt  the START of their most recent session for it, the same calendar the analytics use
 */
public record RecentActivityResponse(Long activityId, String name, Category category,
                                     long timesLogged, LocalDateTime lastUsedAt) {
}
