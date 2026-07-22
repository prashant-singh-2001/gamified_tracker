package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;

import java.time.LocalDateTime;

public record RankCardDto(RankTier tier, int overallLevel, double totalXp, double percentile,
                           int position, int totalUsers, LocalDateTime updatedAt) {
}
