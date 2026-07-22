package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dto.UserXpProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class LevelTrackerRepositoryTest {

    @Autowired
    private LevelTrackerRepository levelTrackerRepository;

    @Test
    void testFindByUserIdAndActivityId() {
        // Arrange
        Long userId = 1L;
        Long activityId = 1L;

        LevelTracker tracker = LevelTracker.builder()
                .userId(userId)
                .activityId(activityId)
                .level(5)
                .totalXp(500.0)
                .currentLevelXp(250.0)
                .build();

        LevelTracker saved = levelTrackerRepository.save(tracker);

        // Act
        Optional<LevelTracker> result = levelTrackerRepository.findByUserIdAndActivityId(userId, activityId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(saved.getId(), result.get().getId());
        assertEquals(userId, result.get().getUserId());
        assertEquals(activityId, result.get().getActivityId());
        assertEquals(5, result.get().getLevel());
    }

    @Test
    void testFindByUserIdAndActivityIdNotFound() {
        // Arrange
        Long userId = 99L;
        Long activityId = 99L;

        // Act
        Optional<LevelTracker> result = levelTrackerRepository.findByUserIdAndActivityId(userId, activityId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindAllByUserId() {
        // Arrange
        Long userId = 2L;

        LevelTracker tracker1 = LevelTracker.builder()
                .userId(userId)
                .activityId(1L)
                .level(3)
                .totalXp(300.0)
                .currentLevelXp(150.0)
                .build();

        LevelTracker tracker2 = LevelTracker.builder()
                .userId(userId)
                .activityId(2L)
                .level(2)
                .totalXp(200.0)
                .currentLevelXp(100.0)
                .build();

        LevelTracker tracker3 = LevelTracker.builder()
                .userId(userId)
                .activityId(3L)
                .level(4)
                .totalXp(400.0)
                .currentLevelXp(200.0)
                .build();

        levelTrackerRepository.save(tracker1);
        levelTrackerRepository.save(tracker2);
        levelTrackerRepository.save(tracker3);

        // Act
        List<LevelTracker> results = levelTrackerRepository.findAllByUserId(userId);

        // Assert
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(t -> t.getUserId().equals(userId)));
    }

    @Test
    void testFindAllByUserIdNoResults() {
        // Arrange
        Long userId = 99L;

        // Act
        List<LevelTracker> results = levelTrackerRepository.findAllByUserId(userId);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void testFindAllByActivityId() {
        // Arrange
        Long activityId = 1L;

        LevelTracker tracker1 = LevelTracker.builder()
                .userId(1L)
                .activityId(activityId)
                .level(3)
                .totalXp(300.0)
                .currentLevelXp(150.0)
                .build();

        LevelTracker tracker2 = LevelTracker.builder()
                .userId(2L)
                .activityId(activityId)
                .level(2)
                .totalXp(200.0)
                .currentLevelXp(100.0)
                .build();

        LevelTracker tracker3 = LevelTracker.builder()
                .userId(3L)
                .activityId(activityId)
                .level(5)
                .totalXp(500.0)
                .currentLevelXp(250.0)
                .build();

        levelTrackerRepository.save(tracker1);
        levelTrackerRepository.save(tracker2);
        levelTrackerRepository.save(tracker3);

        // Act
        List<LevelTracker> results = levelTrackerRepository.findAllByActivityId(activityId);

        // Assert
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(t -> t.getActivityId().equals(activityId)));
    }

    @Test
    void testFindAllByActivityIdNoResults() {
        // Arrange
        Long activityId = 99L;

        // Act
        List<LevelTracker> results = levelTrackerRepository.findAllByActivityId(activityId);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetTotalXpByUserId() {
        // Arrange
        Long userId = 3L;

        LevelTracker tracker1 = LevelTracker.builder()
                .userId(userId)
                .activityId(1L)
                .level(2)
                .totalXp(250.0)
                .currentLevelXp(100.0)
                .build();

        LevelTracker tracker2 = LevelTracker.builder()
                .userId(userId)
                .activityId(2L)
                .level(3)
                .totalXp(300.0)
                .currentLevelXp(150.0)
                .build();

        LevelTracker tracker3 = LevelTracker.builder()
                .userId(userId)
                .activityId(3L)
                .level(1)
                .totalXp(100.0)
                .currentLevelXp(50.0)
                .build();

        levelTrackerRepository.save(tracker1);
        levelTrackerRepository.save(tracker2);
        levelTrackerRepository.save(tracker3);

        // Act
        Double totalXp = levelTrackerRepository.getTotalXpByUserId(userId);

        // Assert
        assertNotNull(totalXp);
        assertEquals(650.0, totalXp);
    }

    @Test
    void testGetTotalXpByUserIdNoRecords() {
        // Arrange
        Long userId = 99L;

        // Act
        Double totalXp = levelTrackerRepository.getTotalXpByUserId(userId);

        // Assert
        assertNotNull(totalXp);
        assertEquals(0.0, totalXp);
    }

    @Test
    void testFindGlobalRanking_ordersBySumOfTotalXpDescending() {
        // Arrange — a user's global rank is the SUM of totalXp across ALL their activities
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(10L).activityId(1L).level(1).totalXp(100.0).currentLevelXp(100.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(10L).activityId(2L).level(1).totalXp(50.0).currentLevelXp(50.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(20L).activityId(1L).level(2).totalXp(300.0).currentLevelXp(100.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(30L).activityId(1L).level(1).totalXp(60.0).currentLevelXp(60.0).build());

        // Act
        List<UserXpProjection> ranking = levelTrackerRepository.findGlobalRanking(PageRequest.of(0, 10));

        // Assert — user 20 (300) > user 10 (150 = 100 + 50, across 2 activities) > user 30 (60)
        assertEquals(3, ranking.size());
        assertEquals(20L, ranking.get(0).getUserId());
        assertEquals(300.0, ranking.get(0).getTotalXp());
        assertEquals(10L, ranking.get(1).getUserId());
        assertEquals(150.0, ranking.get(1).getTotalXp());
        assertEquals(30L, ranking.get(2).getUserId());
        assertEquals(60.0, ranking.get(2).getTotalXp());
    }

    @Test
    void testFindGlobalRanking_respectsPaging() {
        // Arrange — 5 users with distinct totals: 500, 400, 300, 200, 100
        for (int i = 1; i <= 5; i++) {
            levelTrackerRepository.save(LevelTracker.builder()
                    .userId((long) i).activityId(1L).level(1)
                    .totalXp(i * 100.0).currentLevelXp(i * 100.0).build());
        }

        // Act
        List<UserXpProjection> page0 = levelTrackerRepository.findGlobalRanking(PageRequest.of(0, 2));
        List<UserXpProjection> page1 = levelTrackerRepository.findGlobalRanking(PageRequest.of(1, 2));
        List<UserXpProjection> page2 = levelTrackerRepository.findGlobalRanking(PageRequest.of(2, 2));

        // Assert — page 0: [500, 400], page 1: [300, 200], page 2: [100]
        assertEquals(2, page0.size());
        assertEquals(500.0, page0.get(0).getTotalXp());
        assertEquals(400.0, page0.get(1).getTotalXp());

        assertEquals(2, page1.size());
        assertEquals(300.0, page1.get(0).getTotalXp());
        assertEquals(200.0, page1.get(1).getTotalXp());

        assertEquals(1, page2.size());
        assertEquals(100.0, page2.get(0).getTotalXp());
    }

    @Test
    void testFindActivityRanking_ordersByTotalXpDescendingForGivenActivity() {
        // Arrange — activity 1 has three trackers; activity 2's tracker must NOT appear
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(1L).activityId(1L).level(1).totalXp(50.0).currentLevelXp(50.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(2L).activityId(1L).level(2).totalXp(200.0).currentLevelXp(100.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(3L).activityId(1L).level(1).totalXp(120.0).currentLevelXp(120.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(4L).activityId(2L).level(5).totalXp(999.0).currentLevelXp(999.0).build());

        // Act
        List<UserXpProjection> ranking = levelTrackerRepository.findActivityRanking(1L, PageRequest.of(0, 10));

        // Assert — only activity 1's trackers, ordered by totalXp desc
        assertEquals(3, ranking.size());
        assertEquals(2L, ranking.get(0).getUserId());
        assertEquals(200.0, ranking.get(0).getTotalXp());
        assertEquals(3L, ranking.get(1).getUserId());
        assertEquals(120.0, ranking.get(1).getTotalXp());
        assertEquals(1L, ranking.get(2).getUserId());
        assertEquals(50.0, ranking.get(2).getTotalXp());
        assertTrue(ranking.stream().noneMatch(r -> r.getUserId().equals(4L)));
    }

    @Test
    void testFindActivityRanking_respectsPaging() {
        // Arrange — 4 users on activity 5 with distinct totals: 400, 300, 200, 100
        for (int i = 1; i <= 4; i++) {
            levelTrackerRepository.save(LevelTracker.builder()
                    .userId((long) i).activityId(5L).level(1)
                    .totalXp(i * 100.0).currentLevelXp(i * 100.0).build());
        }

        // Act
        List<UserXpProjection> page0 = levelTrackerRepository.findActivityRanking(5L, PageRequest.of(0, 3));
        List<UserXpProjection> page1 = levelTrackerRepository.findActivityRanking(5L, PageRequest.of(1, 3));

        // Assert
        assertEquals(3, page0.size());
        assertEquals(400.0, page0.get(0).getTotalXp());
        assertEquals(300.0, page0.get(1).getTotalXp());
        assertEquals(200.0, page0.get(2).getTotalXp());

        assertEquals(1, page1.size());
        assertEquals(100.0, page1.get(0).getTotalXp());
    }

    @Test
    void testFindAllUserTotals_groupsAndOrdersDescending() {
        // Arrange — user 40 spans two activities (100 + 50 = 150 total); user 41 has one (300)
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(40L).activityId(1L).level(1).totalXp(100.0).currentLevelXp(100.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(40L).activityId(2L).level(1).totalXp(50.0).currentLevelXp(50.0).build());
        levelTrackerRepository.save(LevelTracker.builder()
                .userId(41L).activityId(1L).level(2).totalXp(300.0).currentLevelXp(100.0).build());

        // Act
        List<UserXpProjection> totals = levelTrackerRepository.findAllUserTotals();

        // Assert — user 41 (300) outranks user 40 (150 summed across activities)
        assertEquals(2, totals.size());
        assertEquals(41L, totals.get(0).getUserId());
        assertEquals(300.0, totals.get(0).getTotalXp());
        assertEquals(40L, totals.get(1).getUserId());
        assertEquals(150.0, totals.get(1).getTotalXp());
    }
}

