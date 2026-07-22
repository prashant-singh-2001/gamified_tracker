package com.tracker.gamification.service;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;
import com.tracker.gamification.repository.UserRankRepository;
import com.tracker.gamification.service.impl.RankServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rank Service Tests")
public class RankServiceImplTest {

    @Mock
    private UserRankRepository userRankRepository;

    @InjectMocks
    private RankServiceImpl rankService;

    private static UserRank rank(Long userId, RankTier tier, double totalXp, int position) {
        return new UserRank(totalXp, 2, tier, 0.2, position, 10, LocalDateTime.now(), userId);
    }

    @Test
    @DisplayName("getRankCard maps the user's snapshot to a card")
    void getRankCard_mapsSnapshotToCard() {
        when(userRankRepository.findByUserId(1L)).thenReturn(Optional.of(rank(1L, RankTier.PEAK, 800.0, 3)));

        Optional<RankCardDto> card = rankService.getRankCard(1L);

        assertTrue(card.isPresent());
        assertEquals(RankTier.PEAK, card.get().tier());
        assertEquals(800.0, card.get().totalXp());
        assertEquals(3, card.get().position());
    }

    @Test
    @DisplayName("getRankCard is empty when the user has no snapshot yet")
    void getRankCard_empty_whenNotYetRanked() {
        when(userRankRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertTrue(rankService.getRankCard(99L).isEmpty());
    }

    @Test
    @DisplayName("getTierLeaderboard assigns 1-based within-tier positions")
    void getTierLeaderboard_assignsWithinTierPositions() {
        when(userRankRepository.findByTierOrderByTotalXpDesc(eq(RankTier.SUMMIT), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(
                        rank(1L, RankTier.SUMMIT, 900.0, 1),
                        rank(2L, RankTier.SUMMIT, 850.0, 2)
                ));

        List<RankTierMemberDto> leaderboard = rankService.getTierLeaderboard(RankTier.SUMMIT, 0, 2);

        assertEquals(2, leaderboard.size());
        assertEquals(1, leaderboard.get(0).withinRankPosition());
        assertEquals(1L, leaderboard.get(0).userId());
        assertEquals(2, leaderboard.get(1).withinRankPosition());
    }

    @Test
    @DisplayName("getDistribution reports a count for every tier, including zero counts")
    void getDistribution_reportsEveryTier() {
        for (RankTier tier : RankTier.values()) {
            when(userRankRepository.countByTier(tier)).thenReturn(tier == RankTier.SUMMIT ? 5L : 0L);
        }

        List<RankDistributionDto> distribution = rankService.getDistribution();

        assertEquals(RankTier.values().length, distribution.size());
        assertEquals(5L, distribution.stream()
                .filter(d -> d.tier() == RankTier.SUMMIT)
                .findFirst().orElseThrow().userCount());
    }
}
