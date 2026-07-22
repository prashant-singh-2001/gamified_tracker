package com.tracker.gamification.service;

import com.tracker.gamification.dao.Achievement;
import com.tracker.gamification.dao.CriteriaType;
import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.repository.AchievementRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.UserAchievementRepository;
import com.tracker.gamification.service.impl.AchievementServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Achievement Service Tests")
public class AchievementServiceImplTest {

    @Mock
    private LevelTrackerRepository levelTrackerRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @InjectMocks
    private AchievementServiceImpl achievementService;

    private static Achievement achievement(Long id, String code, CriteriaType type, long threshold) {
        return achievement(id, code, type, threshold, null);
    }

    private static Achievement achievement(Long id, String code, CriteriaType type, long threshold, Long activityId) {
        return new Achievement(id, code, code, code, type, threshold, activityId, true);
    }

    private static LevelTracker tracker(Long activityId, Integer level, double totalXp, int logCount) {
        return LevelTracker.builder()
                .userId(1L)
                .activityId(activityId)
                .level(level)
                .totalXp(totalXp)
                .currentLevelXp(totalXp)
                .logCount(logCount)
                .build();
    }

    @Test
    @DisplayName("grants XP_1000 when total XP crosses the threshold")
    void evaluateAndAward_grantsTotalXpBadge_whenThresholdCrossed() {
        // Arrange
        Achievement xp1000 = achievement(1L, "XP_1000", CriteriaType.TOTAL_XP, 1000);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(1200.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of(tracker(1L, 3, 1200.0, 5)));
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(xp1000));
        when(userAchievementRepository.grantIfAbsent(1L, 1L)).thenReturn(1);

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert
        assertEquals(1, unlocked.size());
        assertEquals("XP_1000", unlocked.get(0).getCode());
        verify(userAchievementRepository).grantIfAbsent(1L, 1L);
    }

    @Test
    @DisplayName("grants LEVEL_5 when the max level across any activity reaches the threshold")
    void evaluateAndAward_grantsLevelBadge_whenMaxLevelReached() {
        // Arrange — user is level 5 on activity 2, but only level 1 on activity 1: max() must win
        Achievement level5 = achievement(2L, "LEVEL_5", CriteriaType.REACH_LEVEL_ANY, 5);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(900.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of(
                tracker(1L, 1, 100.0, 2),
                tracker(2L, 5, 800.0, 3)
        ));
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(level5));
        when(userAchievementRepository.grantIfAbsent(1L, 2L)).thenReturn(1);

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert
        assertEquals(1, unlocked.size());
        assertEquals("LEVEL_5", unlocked.get(0).getCode());
    }

    @Test
    @DisplayName("ACTIVITIES_LOGGED sums logCount across every tracker, not just one activity")
    void evaluateAndAward_sumsLogCountAcrossTrackers_forActivitiesLoggedBadge() {
        // Arrange — DEDICATED needs 50 logs total; no single activity has that many on its own
        Achievement dedicated = achievement(3L, "DEDICATED", CriteriaType.ACTIVITIES_LOGGED, 50);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(500.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of(
                tracker(1L, 2, 200.0, 30),
                tracker(2L, 1, 300.0, 20)
        ));
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(dedicated));
        when(userAchievementRepository.grantIfAbsent(1L, 3L)).thenReturn(1);

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert — 30 + 20 = 50, exactly at threshold
        assertEquals(1, unlocked.size());
        assertEquals("DEDICATED", unlocked.get(0).getCode());
    }

    @Test
    @DisplayName("zero logged activities does not grant a badge requiring at least one")
    void evaluateAndAward_doesNotGrant_whenBelowThreshold() {
        // Arrange
        Achievement firstSteps = achievement(4L, "FIRST_STEPS", CriteriaType.ACTIVITIES_LOGGED, 1);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(0.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of());
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(firstSteps));

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert — zero trackers means zero logs; 0 >= 1 is false
        assertTrue(unlocked.isEmpty());
        verify(userAchievementRepository, never()).grantIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("re-running after a badge is already owned grants nothing new")
    void evaluateAndAward_grantsNothing_whenAlreadyOwned() {
        // Arrange — criteria is satisfied, but grantIfAbsent reports it's already owned (returns 0)
        Achievement xp1000 = achievement(1L, "XP_1000", CriteriaType.TOTAL_XP, 1000);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(1500.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of(tracker(1L, 4, 1500.0, 10)));
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(xp1000));
        when(userAchievementRepository.grantIfAbsent(1L, 1L)).thenReturn(0);

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert
        assertTrue(unlocked.isEmpty());
    }

    @Test
    @DisplayName("ACTIVITY_LEVEL checks the specific activity's level, defaulting to 0 when untouched")
    void evaluateAndAward_checksSpecificActivityLevel_defaultingToZeroWhenUntouched() {
        // Arrange — badge is scoped to activity 9; user has no tracker for activity 9 at all
        Achievement activityBadge = achievement(5L, "ACTIVITY_9_MASTER", CriteriaType.ACTIVITY_LEVEL, 3, 9L);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(100.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of(tracker(1L, 5, 100.0, 1)));
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(activityBadge));

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert — activity 9 has no tracker, so its level defaults to 0, which is < 3
        assertTrue(unlocked.isEmpty());
        verify(userAchievementRepository, never()).grantIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("ACTIVITY_LEVEL grants when the specific tracked activity meets its own threshold")
    void evaluateAndAward_grantsActivityLevelBadge_whenThatActivityQualifies() {
        // Arrange
        Achievement activityBadge = achievement(6L, "ACTIVITY_2_MASTER", CriteriaType.ACTIVITY_LEVEL, 3, 2L);
        when(levelTrackerRepository.getTotalXpByUserId(1L)).thenReturn(700.0);
        when(levelTrackerRepository.findAllByUserId(1L)).thenReturn(List.of(
                tracker(1L, 1, 100.0, 1),
                tracker(2L, 3, 600.0, 4)
        ));
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(activityBadge));
        when(userAchievementRepository.grantIfAbsent(1L, 6L)).thenReturn(1);

        // Act
        List<Achievement> unlocked = achievementService.evaluateAndAward(1L);

        // Assert
        assertEquals(1, unlocked.size());
        assertEquals("ACTIVITY_2_MASTER", unlocked.get(0).getCode());
    }
}
