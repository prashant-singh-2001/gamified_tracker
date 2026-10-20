package com.tracker.activity.dto;

import com.tracker.activity.dao.Category;

import java.time.LocalDateTime;

/** Boxed getters, as in gamification's UserXpProjection: aggregates can be null for an empty group. */
public interface RecentActivityProjection {
    Long getActivityId();

    String getActivityName();

    Category getCategory();

    Long getTimesLogged();

    LocalDateTime getLastUsedAt();
}
