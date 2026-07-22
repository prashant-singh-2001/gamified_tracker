package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;
import com.tracker.gamification.repository.UserRankRepository;
import com.tracker.gamification.service.RankService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@AllArgsConstructor
public class RankServiceImpl implements RankService {

    private final UserRankRepository userRankRepository;

    @Override
    public Optional<RankCardDto> getRankCard(Long userId) {
        return userRankRepository.findByUserId(userId).map(this::mapToCard);
    }

    @Override
    public List<RankTierMemberDto> getTierLeaderboard(RankTier tier, int page, int size) {
        List<UserRank> members = userRankRepository.findByTierOrderByTotalXpDesc(tier, PageRequest.of(page, size));

        int offset = page * size;
        return IntStream.range(0, members.size())
                .mapToObj(i -> mapToMember(members.get(i), offset + i + 1))
                .toList();
    }

    @Override
    public List<RankDistributionDto> getDistribution() {
        return Arrays.stream(RankTier.values())
                .map(tier -> new RankDistributionDto(tier, userRankRepository.countByTier(tier)))
                .toList();
    }

    private RankCardDto mapToCard(UserRank userRank) {
        return new RankCardDto(
                userRank.getTier(),
                userRank.getOverallLevel(),
                userRank.getTotalXp(),
                userRank.getPercentile(),
                userRank.getPosition(),
                userRank.getTotalUsers(),
                userRank.getUpdatedAt()
        );
    }

    private RankTierMemberDto mapToMember(UserRank userRank, int withinRankPosition) {
        return new RankTierMemberDto(
                withinRankPosition,
                userRank.getUserId(),
                userRank.getTotalXp(),
                userRank.getOverallLevel(),
                userRank.getTier()
        );
    }
}
