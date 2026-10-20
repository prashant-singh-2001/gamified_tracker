package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.domain.RankMovement;

// Named distinctly from the existing LeaderboardEntryDto (a different feature's global/
// per-activity leaderboard row) to avoid a same-name clash in the dto package.
//
// totalXp is lifetime XP; the board is ordered by rankScore (recency-weighted XP, issue 135), which is
// also what division and the movement fields are about. See RankCardDto for their meaning.
public record RankTierMemberDto(int withinRankPosition, Long userId, double totalXp,
                                 int overallLevel, RankTier tier,
                                 double rankScore, int division,
                                 Integer positionChange, RankMovement.Trend trend) {
}
