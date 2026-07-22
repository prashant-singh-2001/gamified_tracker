package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;

public record RankDistributionDto(RankTier tier, long userCount) {
}
