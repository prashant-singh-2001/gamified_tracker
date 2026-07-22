package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;

// Named distinctly from the existing LeaderboardEntryDto (a different feature's global/
// per-activity leaderboard row) to avoid a same-name clash in the dto package.
public record RankTierMemberDto(int withinRankPosition, Long userId, double totalXp,
                                 int overallLevel, RankTier tier) {
}
