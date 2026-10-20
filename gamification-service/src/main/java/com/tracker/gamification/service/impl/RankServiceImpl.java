package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;
import com.tracker.gamification.dto.TierFloorProjection;
import com.tracker.gamification.repository.UserRankRepository;
import com.tracker.gamification.service.RankService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
        // One grouped query: the lowest XP in every POPULATED tier (an empty tier has no row).
        Map<RankTier, Double> floors = new EnumMap<>(RankTier.class);
        for (TierFloorProjection floor : userRankRepository.findTierFloors()) {
            floors.put(floor.getTier(), floor.getMinTotalXp());
        }

        RankTier tier = userRank.getTier();

        // The next BETTER tier that actually has members. The enum runs best-first (SUMMIT is
        // ordinal 0), so walk towards 0. An empty tier is skipped: tiers are assigned by relative
        // position, so there is no XP you could earn to "reach" a bracket nobody currently occupies.
        RankTier nextTier = null;
        Double xpToNextTier = null;
        RankTier[] tiers = RankTier.values();
        for (int i = tier.ordinal() - 1; i >= 0; i--) {
            Double floor = floors.get(tiers[i]);
            if (floor != null) {
                nextTier = tiers[i];
                // To match the weakest member of that tier. Never negative: a stale snapshot can put
                // the caller at or past that floor, which is "0 more", not a negative number.
                xpToNextTier = Math.max(0.0, floor - userRank.getTotalXp());
                break;
            }
        }

        return new RankCardDto(
                tier,
                userRank.getOverallLevel(),
                userRank.getTotalXp(),
                userRank.getPercentile(),
                userRank.getPosition(),
                userRank.getTotalUsers(),
                userRank.getUpdatedAt(),
                true,
                floors.get(tier),
                nextTier,
                xpToNextTier
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
