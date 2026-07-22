package com.tracker.gamification.service;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;

import java.util.List;
import java.util.Optional;

public interface RankService {

    Optional<RankCardDto> getRankCard(Long userId);

    List<RankTierMemberDto> getTierLeaderboard(RankTier tier, int page, int size);

    List<RankDistributionDto> getDistribution();
}
