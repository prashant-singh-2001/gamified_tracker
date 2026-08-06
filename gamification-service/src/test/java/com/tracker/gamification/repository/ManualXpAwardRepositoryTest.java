package com.tracker.gamification.repository;

import com.tracker.gamification.dao.ManualXpAward;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// #74: round-trips the entity against H2 to prove the mapping is correct. This does NOT
// prove V3__create_manual_xp_award.sql is correct — H2 builds its schema from entities,
// not Flyway (ddl-auto=create-drop for tests) — a real-Postgres smoke test is required
// separately to validate the migration itself.
@DataJpaTest
public class ManualXpAwardRepositoryTest {

    @Autowired
    private ManualXpAwardRepository manualXpAwardRepository;

    @Test
    void testSaveAndFindById() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        ManualXpAward award = ManualXpAward.builder()
                .actorUserId(10L)
                .targetUserId(2L)
                .activityId(1L)
                .xp(500.0)
                .awardedAt(now)
                .build();

        // Act
        ManualXpAward saved = manualXpAwardRepository.save(award);
        Optional<ManualXpAward> result = manualXpAwardRepository.findById(saved.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getActorUserId());
        assertEquals(2L, result.get().getTargetUserId());
        assertEquals(1L, result.get().getActivityId());
        assertEquals(500.0, result.get().getXp());
        assertEquals(now, result.get().getAwardedAt());
    }

    @Test
    void testFindByIdNotFound() {
        // Act
        Optional<ManualXpAward> result = manualXpAwardRepository.findById(999L);

        // Assert
        assertTrue(result.isEmpty());
    }
}
