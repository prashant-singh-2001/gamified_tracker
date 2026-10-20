package com.tracker.gamification.service;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;
import com.tracker.gamification.dto.TierFloorProjection;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // A mocked repository cannot return a Spring Data projection proxy; a plain record will do.
    private record Floor(RankTier tier, Double minTotalXp) implements TierFloorProjection {
        @Override
        public RankTier getTier() {
            return tier;
        }

        @Override
        public Double getMinTotalXp() {
            return minTotalXp;
        }
    }

    @Test
    @DisplayName("rank nudge: the next tier is the next BETTER one that has members -- an empty tier is skipped")
    void getRankCard_nudge_skipsAnEmptyTier() {
        // ALPINE user on 500 XP. RIDGE (the adjacent better tier) is empty, so it cannot be reached
        // by earning XP; the next reachable tier is PEAK, whose weakest member holds 2000.
        when(userRankRepository.findByUserId(1L)).thenReturn(Optional.of(rank(1L, RankTier.ALPINE, 500.0, 12)));
        when(userRankRepository.findTierFloors()).thenReturn(List.of(
                new Floor(RankTier.SUMMIT, 5000.0),
                new Floor(RankTier.PEAK, 2000.0),
                new Floor(RankTier.ALPINE, 400.0),
                new Floor(RankTier.ASCENT, 300.0)));

        RankCardDto card = rankService.getRankCard(1L).orElseThrow();

        assertTrue(card.ranked());
        assertEquals(400.0, card.tierCutoffXp(), 1e-9, "lowest XP held in the caller's own tier");
        assertEquals(RankTier.PEAK, card.nextTier());
        assertEquals(1500.0, card.xpToNextTier(), 1e-9);
    }

    @Test
    @DisplayName("rank nudge: the adjacent better tier is used when it is populated")
    void getRankCard_nudge_adjacentTier() {
        when(userRankRepository.findByUserId(2L)).thenReturn(Optional.of(rank(2L, RankTier.PEAK, 2500.0, 2)));
        when(userRankRepository.findTierFloors()).thenReturn(List.of(
                new Floor(RankTier.SUMMIT, 5000.0), new Floor(RankTier.PEAK, 2100.0)));

        RankCardDto card = rankService.getRankCard(2L).orElseThrow();

        assertEquals(RankTier.SUMMIT, card.nextTier());
        assertEquals(2500.0, card.xpToNextTier(), 1e-9);
        assertEquals(2100.0, card.tierCutoffXp(), 1e-9);
    }

    @Test
    @DisplayName("rank nudge: at the top there is no next tier -- nextTier and xpToNextTier are null")
    void getRankCard_nudge_topTier() {
        when(userRankRepository.findByUserId(3L)).thenReturn(Optional.of(rank(3L, RankTier.SUMMIT, 9000.0, 1)));
        when(userRankRepository.findTierFloors()).thenReturn(List.of(
                new Floor(RankTier.SUMMIT, 8000.0), new Floor(RankTier.ALPINE, 100.0)));

        RankCardDto card = rankService.getRankCard(3L).orElseThrow();

        assertNull(card.nextTier());
        assertNull(card.xpToNextTier());
        assertEquals(8000.0, card.tierCutoffXp(), 1e-9);
    }

    @Test
    @DisplayName("rank nudge: nobody better is ranked (sole member of a tier) -- no next tier even though not SUMMIT")
    void getRankCard_nudge_noPopulatedBetterTier() {
        when(userRankRepository.findByUserId(4L)).thenReturn(Optional.of(rank(4L, RankTier.ALPINE, 300.0, 1)));
        when(userRankRepository.findTierFloors()).thenReturn(List.of(new Floor(RankTier.ALPINE, 300.0)));

        RankCardDto card = rankService.getRankCard(4L).orElseThrow();

        assertNull(card.nextTier());
        assertNull(card.xpToNextTier());
    }

    @Test
    @DisplayName("rank nudge: a stale snapshot that already has the caller at or past the floor yields 0, never negative")
    void getRankCard_nudge_neverNegative() {
        when(userRankRepository.findByUserId(5L)).thenReturn(Optional.of(rank(5L, RankTier.ALPINE, 2500.0, 9)));
        when(userRankRepository.findTierFloors()).thenReturn(List.of(
                new Floor(RankTier.PEAK, 2000.0), new Floor(RankTier.ALPINE, 400.0)));

        assertEquals(0.0, rankService.getRankCard(5L).orElseThrow().xpToNextTier(), 1e-9);
    }

    @Test
    @DisplayName("percentile is still reported exactly as stored -- a TOP fraction where 0 is best")
    void getRankCard_percentileIsUnchanged() {
        when(userRankRepository.findByUserId(6L)).thenReturn(Optional.of(rank(6L, RankTier.ALPINE, 500.0, 4)));

        assertEquals(0.2, rankService.getRankCard(6L).orElseThrow().percentile(), 1e-9);
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
