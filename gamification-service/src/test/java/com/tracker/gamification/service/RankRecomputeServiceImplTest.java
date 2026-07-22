package com.tracker.gamification.service;

import com.tracker.gamification.dto.UserXpProjection;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.UserRankRepository;
import com.tracker.gamification.service.impl.RankRecomputeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rank Recompute Service Tests")
public class RankRecomputeServiceImplTest {

    @Mock
    private LevelTrackerRepository levelTrackerRepository;

    @Mock
    private UserRankRepository userRankRepository;

    @Mock
    private OverallLevelService overallLevelService;

    @InjectMocks
    private RankRecomputeServiceImpl rankRecomputeService;

    private record XpRow(Long userId, Double totalXp) implements UserXpProjection {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public Double getTotalXp() {
            return totalXp;
        }
    }

    @Test
    @DisplayName("recompute returns 0 and upserts nothing when no users are tracked")
    void recompute_doesNothing_whenNoUsersTracked() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of());

        int ranked = rankRecomputeService.recompute();

        assertEquals(0, ranked);
        verify(userRankRepository, never())
                .upsert(any(), anyDouble(), anyInt(), any(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("a lone user is ranked SUMMIT at position 1")
    void recompute_singleUser_isSummitAtPositionOne() {
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(new XpRow(1L, 500.0)));
        when(overallLevelService.overallLevelFor(500.0)).thenReturn(3);

        int ranked = rankRecomputeService.recompute();

        assertEquals(1, ranked);
        verify(userRankRepository).upsert(1L, 500.0, 3, "SUMMIT", 0.0, 1, 1);
    }

    @Test
    @DisplayName("tied users share the tie group's minimum position, not consecutive raw positions")
    void recompute_tiedUsers_shareMinimumPosition() {
        // Arrange — users 2 and 3 are tied at 400.0; user 1 leads at 500.0, user 4 trails at 100.0
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(List.of(
                new XpRow(1L, 500.0),
                new XpRow(2L, 400.0),
                new XpRow(3L, 400.0),
                new XpRow(4L, 100.0)
        ));
        when(overallLevelService.overallLevelFor(anyDouble())).thenReturn(1);

        rankRecomputeService.recompute();

        // Both tied users land on position 2 (the tie group's minimum), not 2 and 3
        verify(userRankRepository).upsert(eq(1L), eq(500.0), anyInt(), any(), eq(0.0), eq(1), eq(4));
        verify(userRankRepository).upsert(eq(2L), eq(400.0), anyInt(), any(), eq(0.25), eq(2), eq(4));
        verify(userRankRepository).upsert(eq(3L), eq(400.0), anyInt(), any(), eq(0.25), eq(2), eq(4));
        // user 4 continues from the raw sequential count (4), not the tie-inflated position
        verify(userRankRepository).upsert(eq(4L), eq(100.0), anyInt(), any(), eq(0.75), eq(4), eq(4));
    }

    @Test
    @DisplayName("20 users split into the exact tier bracket sizes the percentile formula implies")
    void recompute_twentyUsers_splitIntoExpectedTierCounts() {
        // Arrange — 20 distinct, strictly descending totals: no ties to complicate the counts
        List<UserXpProjection> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(new XpRow((long) (i + 1), 2000.0 - i * 10));
        }
        when(levelTrackerRepository.findAllUserTotals()).thenReturn(rows);
        when(overallLevelService.overallLevelFor(anyDouble())).thenReturn(1);

        rankRecomputeService.recompute();

        // topFraction = (position-1)/20 buckets positions 1..20 into exactly these tier counts
        verify(userRankRepository, times(1)).upsert(any(), anyDouble(), anyInt(), eq("SUMMIT"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(2)).upsert(any(), anyDouble(), anyInt(), eq("PEAK"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(2)).upsert(any(), anyDouble(), anyInt(), eq("RIDGE"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(5)).upsert(any(), anyDouble(), anyInt(), eq("ALPINE"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(2)).upsert(any(), anyDouble(), anyInt(), eq("ASCENT"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(3)).upsert(any(), anyDouble(), anyInt(), eq("HIGHLAND"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(2)).upsert(any(), anyDouble(), anyInt(), eq("FOOTHILL"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(2)).upsert(any(), anyDouble(), anyInt(), eq("TRAILHEAD"), anyDouble(), anyInt(), eq(20));
        verify(userRankRepository, times(1)).upsert(any(), anyDouble(), anyInt(), eq("BASECAMP"), anyDouble(), anyInt(), eq(20));
    }
}
