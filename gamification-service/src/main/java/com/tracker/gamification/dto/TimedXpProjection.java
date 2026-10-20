package com.tracker.gamification.dto;

import java.time.LocalDateTime;

/** One award of XP to a user at a moment, the raw material of the recency-decayed rank score. */
public interface TimedXpProjection {
    Long getUserId();

    Double getXp();

    LocalDateTime getOccurredAt();
}
