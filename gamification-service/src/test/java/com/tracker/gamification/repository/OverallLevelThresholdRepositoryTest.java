package com.tracker.gamification.repository;

import com.tracker.gamification.dao.OverallLevelThreshold;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class OverallLevelThresholdRepositoryTest {

    @Autowired
    private OverallLevelThresholdRepository overallLevelThresholdRepository;

    private void seedCurve() {
        overallLevelThresholdRepository.save(new OverallLevelThreshold(1, 0));
        overallLevelThresholdRepository.save(new OverallLevelThreshold(2, 100));
        overallLevelThresholdRepository.save(new OverallLevelThreshold(3, 250));
        overallLevelThresholdRepository.save(new OverallLevelThreshold(4, 500));
        overallLevelThresholdRepository.save(new OverallLevelThreshold(5, 1000));
    }

    @Test
    void findReachedLevel_returnsHighestLevelWhoseThresholdIsCleared() {
        seedCurve();

        assertEquals(3, overallLevelThresholdRepository.findReachedLevel(300, PageRequest.of(0, 1)).get(0));
    }

    @Test
    void findReachedLevel_exactlyAtThreshold_countsAsReached() {
        seedCurve();

        assertEquals(4, overallLevelThresholdRepository.findReachedLevel(500, PageRequest.of(0, 1)).get(0));
    }

    @Test
    void findReachedLevel_belowLowestThreshold_isEmpty() {
        // Arrange — a curve whose lowest rung requires 50 XP; 10 XP clears nothing
        overallLevelThresholdRepository.save(new OverallLevelThreshold(1, 50));

        assertTrue(overallLevelThresholdRepository.findReachedLevel(10, PageRequest.of(0, 1)).isEmpty());
    }
}
