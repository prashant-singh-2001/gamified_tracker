package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LogXpOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class LogXpOutcomeRepositoryTest {

    @Autowired
    private LogXpOutcomeRepository repository;

    private static LogXpOutcome outcome(long logId, long userId, double xp) {
        return LogXpOutcome.builder()
                .logId(logId)
                .userId(userId)
                .activityId(5L)
                .xpApplied(xp)
                .previousLevel(2)
                .newLevel(3)
                .leveledUp(true)
                .appliedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void findByLogIdAndUserId_returnsTheRowForItsOwner() {
        repository.saveAndFlush(outcome(42L, 1L, 120.5));

        LogXpOutcome found = repository.findByLogIdAndUserId(42L, 1L).orElseThrow();

        assertEquals(120.5, found.getXpApplied(), 1e-9);
        assertEquals(2, found.getPreviousLevel());
        assertEquals(3, found.getNewLevel());
        assertTrue(found.isLeveledUp());
    }

    @Test
    void findByLogIdAndUserId_isEmptyForAnotherUser_soOwnershipIsPartOfTheLookup() {
        repository.saveAndFlush(outcome(42L, 1L, 10.0));

        assertTrue(repository.findByLogIdAndUserId(42L, 2L).isEmpty());
    }

    @Test
    void findByLogIdAndUserId_isEmptyForAnUnknownLog() {
        assertTrue(repository.findByLogIdAndUserId(999L, 1L).isEmpty());
    }

    // Same trap #82 closed for ProcessedEvent: a manually-assigned @Id makes Spring Data's default
    // isNew() always false, so save() would merge() and a duplicate would silently UPDATE the first
    // outcome. Persistable.isNew() == true forces persist(), so a duplicate fails loudly.
    @Test
    void saveAndFlush_sameLogTwice_throwsInsteadOfSilentlyOverwriting() {
        repository.saveAndFlush(outcome(42L, 1L, 10.0));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(outcome(42L, 1L, 999.0)));
    }
}
