package com.tracker.gamification.repository;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dao.ActivityLevelThresholdId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ActivityLevelThresholdRepositoryTest {

    @Autowired
    private ActivityLevelThresholdRepository activityLevelThresholdRepository;

    @Test
    void testFindReachedLevels() {

        // Arrange
        Long activityId = 1L;

        activityLevelThresholdRepository.saveAll(List.of(
                createThreshold(activityId, 1, 0),
                createThreshold(activityId, 2, 100),
                createThreshold(activityId, 3, 300)
        ));

        // Act
        List<ActivityLevelThreshold> reachedLevels =
                activityLevelThresholdRepository.findReachedLevels(
                        activityId,
                        250.0,
                        PageRequest.of(0, 10)
                );

        // Assert
        assertEquals(2, reachedLevels.size());
        assertEquals(2, reachedLevels.get(0).getId().getLevel());
        assertEquals(1, reachedLevels.get(1).getId().getLevel());
    }

    @Test
    void testFindReachedLevelsNoResults() {

        // Arrange
        activityLevelThresholdRepository.save(
                createThreshold(2L, 1, 500)
        );

        // Act
        List<ActivityLevelThreshold> reachedLevels =
                activityLevelThresholdRepository.findReachedLevels(
                        2L,
                        100.0,
                        PageRequest.of(0, 10)
                );

        // Assert
        assertTrue(reachedLevels.isEmpty());
    }

    @Test
    void testFindReachedLevelsWithPagination() {

        Long activityId = 1L;

        activityLevelThresholdRepository.saveAll(List.of(
                createThreshold(activityId, 1, 0),
                createThreshold(activityId, 2, 100),
                createThreshold(activityId, 3, 300)
        ));

        var reachedLevels = activityLevelThresholdRepository.findReachedLevels(
                activityId,
                250.0,
                PageRequest.of(0, 1)
        );

        assertEquals(1, reachedLevels.size());
        assertEquals(2, reachedLevels.get(0).getId().getLevel());
    }

    @Test
    void findNextLevels_returnsLowestLevelAheadOfXp() {

        // Arrange
        Long activityId = 1L;

        activityLevelThresholdRepository.saveAll(List.of(
                createThreshold(activityId, 2, 200),
                createThreshold(activityId, 3, 500)
        ));

        // Act
        List<ActivityLevelThreshold> next =
                activityLevelThresholdRepository.findNextLevels(activityId, 300.0, PageRequest.of(0, 1));

        // Assert
        assertEquals(1, next.size());
        assertEquals(3, next.get(0).getId().getLevel());
    }

    @Test
    void findNextLevels_returnsEmptyAtMaxLevel() {

        // Arrange
        Long activityId = 1L;

        activityLevelThresholdRepository.save(createThreshold(activityId, 2, 200));

        // Act
        List<ActivityLevelThreshold> next =
                activityLevelThresholdRepository.findNextLevels(activityId, 999.0, PageRequest.of(0, 1));

        // Assert
        assertTrue(next.isEmpty());
    }

    @Test
    void findAllForActivities_returnsRowsForEveryRequestedActivityOrderedByLevel() {

        // Arrange
        activityLevelThresholdRepository.saveAll(List.of(
                createThreshold(1L, 3, 500),
                createThreshold(1L, 2, 200),
                createThreshold(2L, 2, 150)
        ));

        // Act
        List<ActivityLevelThreshold> rows =
                activityLevelThresholdRepository.findAllForActivities(List.of(1L, 2L));

        // Assert
        assertEquals(3, rows.size());
        assertEquals(2, rows.get(0).getId().getLevel());   // activity 1, level 2 sorts first
        assertEquals(1L, rows.get(0).getId().getActivityId());
    }

    @Test
    void countForActivity_countsOnlyThatActivitysRows() {

        // Arrange
        activityLevelThresholdRepository.saveAll(List.of(
                createThreshold(10L, 2, 100),
                createThreshold(10L, 3, 300),
                createThreshold(11L, 2, 150)
        ));

        // Act & Assert
        assertEquals(2, activityLevelThresholdRepository.countForActivity(10L));
        assertEquals(1, activityLevelThresholdRepository.countForActivity(11L));
    }

    @Test
    void countForActivity_returnsZeroWhenActivityHasNoRows() {
        assertEquals(0, activityLevelThresholdRepository.countForActivity(999L));
    }

    @Test
    void findAllForActivity_returnsRowsOrderedByLevelAscending() {

        // Arrange — inserted out of order on purpose
        activityLevelThresholdRepository.saveAll(List.of(
                createThreshold(10L, 3, 300),
                createThreshold(10L, 1, 0),
                createThreshold(10L, 2, 100),
                createThreshold(11L, 5, 999) // a different activity — must not leak in
        ));

        // Act
        List<ActivityLevelThreshold> rows = activityLevelThresholdRepository.findAllForActivity(10L);

        // Assert
        assertEquals(3, rows.size());
        assertEquals(1, rows.get(0).getId().getLevel());
        assertEquals(2, rows.get(1).getId().getLevel());
        assertEquals(3, rows.get(2).getId().getLevel());
    }

    private ActivityLevelThreshold createThreshold(
            Long activityId,
            int level,
            double xpRequired) {

        return ActivityLevelThreshold.builder()
                .id(ActivityLevelThresholdId.builder()
                        .activityId(activityId)
                        .level(level)
                        .build())
                .xpRequired(xpRequired)
                .build();
    }
}