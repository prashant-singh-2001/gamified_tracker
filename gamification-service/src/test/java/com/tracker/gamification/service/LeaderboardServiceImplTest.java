package com.tracker.gamification.service;

import com.tracker.gamification.dto.LeaderboardEntryDto;
import com.tracker.gamification.dto.UserXpProjection;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.service.impl.LeaderboardServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Leaderboard Service Tests")
public class LeaderboardServiceImplTest {

    @Mock
    private LevelTrackerRepository levelTrackerRepository;

    @InjectMocks
    private LeaderboardServiceImpl leaderboardService;

    @Test
    @DisplayName("getGlobalLeaderboard assigns ranks 1..N in the order the repository returns them")
    void getGlobalLeaderboard_assignsSequentialRanks_onFirstPage() {
        // Arrange
        when(levelTrackerRepository.findGlobalRanking(eq(PageRequest.of(0, 3))))
                .thenReturn(List.of(
                        new XpRow(1L, 300.0),
                        new XpRow(2L, 200.0),
                        new XpRow(3L, 100.0)
                ));

        // Act
        List<LeaderboardEntryDto> result = leaderboardService.getGlobalLeaderboard(0, 3);

        // Assert
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).rank());
        assertEquals(1L, result.get(0).userId());
        assertEquals(300.0, result.get(0).totalXp());
        assertEquals(2, result.get(1).rank());
        assertEquals(3, result.get(2).rank());
        verify(levelTrackerRepository).findGlobalRanking(eq(PageRequest.of(0, 3)));
    }

    @Test
    @DisplayName("getGlobalLeaderboard continues ranking across a page boundary")
    void getGlobalLeaderboard_continuesRankAcrossPageBoundary() {
        // Arrange — page 1 (0-based) with size 3 starts at rank 4
        when(levelTrackerRepository.findGlobalRanking(eq(PageRequest.of(1, 3))))
                .thenReturn(List.of(
                        new XpRow(4L, 90.0),
                        new XpRow(5L, 80.0)
                ));

        // Act
        List<LeaderboardEntryDto> result = leaderboardService.getGlobalLeaderboard(1, 3);

        // Assert
        assertEquals(2, result.size());
        assertEquals(4, result.get(0).rank());
        assertEquals(4L, result.get(0).userId());
        assertEquals(5, result.get(1).rank());
        assertEquals(5L, result.get(1).userId());
    }

    @Test
    @DisplayName("getGlobalLeaderboard gives tied users consecutive ranks, broken by repository order")
    void getGlobalLeaderboard_breaksTiesByRepositoryOrder() {
        // Arrange — users 7 and 8 are tied at 500.0 XP. The service has no tie-collapsing
        // logic, so rank is assigned purely by list position: 1, 2, 3 — never a shared rank.
        when(levelTrackerRepository.findGlobalRanking(eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(
                        new XpRow(7L, 500.0),
                        new XpRow(8L, 500.0),
                        new XpRow(9L, 400.0)
                ));

        // Act
        List<LeaderboardEntryDto> result = leaderboardService.getGlobalLeaderboard(0, 10);

        // Assert
        assertEquals(1, result.get(0).rank());
        assertEquals(7L, result.get(0).userId());
        assertEquals(2, result.get(1).rank());
        assertEquals(8L, result.get(1).userId());
        assertEquals(500.0, result.get(1).totalXp());
        assertEquals(3, result.get(2).rank());
    }

    @Test
    @DisplayName("getGlobalLeaderboard returns an empty list when no one is tracked yet")
    void getGlobalLeaderboard_returnsEmptyList_whenNoUsersTracked() {
        // Arrange
        when(levelTrackerRepository.findGlobalRanking(eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());

        // Act
        List<LeaderboardEntryDto> result = leaderboardService.getGlobalLeaderboard(0, 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getActivityLeaderboard delegates to the activity-scoped query and ranks the result")
    void getActivityLeaderboard_ranksWithinTheGivenActivity() {
        // Arrange
        when(levelTrackerRepository.findActivityRanking(eq(1L), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(
                        new XpRow(2L, 200.0),
                        new XpRow(3L, 120.0)
                ));

        // Act
        List<LeaderboardEntryDto> result = leaderboardService.getActivityLeaderboard(1L, 0, 2);

        // Assert
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).rank());
        assertEquals(2L, result.get(0).userId());
        assertEquals(2, result.get(1).rank());
        verify(levelTrackerRepository).findActivityRanking(eq(1L), eq(PageRequest.of(0, 2)));
    }

    @Test
    @DisplayName("getMyRank returns the count of users ahead, plus one")
    void getMyRank_returnsUsersAheadPlusOne() {
        // Arrange
        when(levelTrackerRepository.getTotalXpByUserId(9L)).thenReturn(250.0);
        when(levelTrackerRepository.countUsersAhead(250.0)).thenReturn(4L);

        // Act
        long rank = leaderboardService.getMyRank(9L);

        // Assert
        assertEquals(5, rank);
    }

    @Test
    @DisplayName("getMyRank returns 1 when no one has more XP")
    void getMyRank_returnsOne_whenNoOneIsAhead() {
        // Arrange
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(1000.0);
        when(levelTrackerRepository.countUsersAhead(1000.0)).thenReturn(0L);

        // Act
        long rank = leaderboardService.getMyRank(1L);

        // Assert
        assertEquals(1, rank);
    }

    // Spring Data only proxies projection interfaces for real query results, so a mocked
    // repository needs a plain implementation to hand back instead.
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
}
