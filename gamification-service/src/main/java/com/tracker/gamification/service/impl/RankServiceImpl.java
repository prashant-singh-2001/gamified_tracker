package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.domain.RankMovement;
import com.tracker.gamification.dto.DivisionFloorProjection;
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
import java.util.HashMap;
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
        List<UserRank> members = userRankRepository.findByTierOrderByRankScoreDesc(tier, PageRequest.of(page, size));

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
        // One grouped query each: the lowest rank score in every POPULATED tier and every POPULATED
        // division (an empty one has no row).
        Map<RankTier, Double> tierFloors = new EnumMap<>(RankTier.class);
        for (TierFloorProjection floor : userRankRepository.findTierFloors()) {
            tierFloors.put(floor.getTier(), floor.getMinScore());
        }
        Map<Integer, Double> stepFloors = new HashMap<>();
        for (DivisionFloorProjection floor : userRankRepository.findDivisionFloors()) {
            stepFloors.put(RankMovement.step(floor.getTier(), floor.getDivision()), floor.getMinScore());
        }

        RankTier tier = userRank.getTier();

        // The next BETTER tier that actually has members. The enum runs best-first (SUMMIT is
        // ordinal 0), so walk towards 0. An empty tier is skipped: tiers are assigned by relative
        // position, so there is no score you could earn to "reach" a bracket nobody currently occupies.
        RankTier nextTier = null;
        Double xpToNextTier = null;
        RankTier[] tiers = RankTier.values();
        for (int i = tier.ordinal() - 1; i >= 0; i--) {
            Double floor = tierFloors.get(tiers[i]);
            if (floor != null) {
                nextTier = tiers[i];
                // To match the weakest member of that tier. Never negative: a stale snapshot can put
                // the caller at or past that floor, which is "0 more", not a negative number.
                xpToNextTier = Math.max(0.0, floor - userRank.getRankScore());
                break;
            }
        }

        // The same walk one step at a time: the next better populated DIVISION, which from division 1 is
        // the lowest division of the tier above. This is the nudge that moves between tier lines.
        RankTier nextDivisionTier = null;
        Integer nextDivision = null;
        Double xpToNextDivision = null;
        for (int step = RankMovement.step(tier, userRank.getDivision()) - 1; step >= 0; step--) {
            Double floor = stepFloors.get(step);
            if (floor != null) {
                nextDivisionTier = tiers[step / RankTier.DIVISIONS];
                nextDivision = step % RankTier.DIVISIONS + 1;
                xpToNextDivision = Math.max(0.0, floor - userRank.getRankScore());
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
                tierFloors.get(tier),
                nextTier,
                xpToNextTier,
                userRank.getRankScore(),
                userRank.getDivision(),
                tier.divisionProgress(userRank.getPercentile()),
                nextDivisionTier,
                nextDivision,
                xpToNextDivision,
                RankMovement.positionChange(userRank.getPosition(), userRank.getPreviousPosition()),
                userRank.getPreviousTier(),
                userRank.getPreviousDivision(),
                trendOf(userRank)
        );
    }

    private RankTierMemberDto mapToMember(UserRank userRank, int withinRankPosition) {
        return new RankTierMemberDto(
                withinRankPosition,
                userRank.getUserId(),
                userRank.getTotalXp(),
                userRank.getOverallLevel(),
                userRank.getTier(),
                userRank.getRankScore(),
                userRank.getDivision(),
                RankMovement.positionChange(userRank.getPosition(), userRank.getPreviousPosition()),
                trendOf(userRank)
        );
    }

    private static RankMovement.Trend trendOf(UserRank userRank) {
        return RankMovement.trend(userRank.getTier(), userRank.getDivision(), userRank.getPosition(),
                userRank.getPreviousTier(), userRank.getPreviousDivision(), userRank.getPreviousPosition());
    }
}
