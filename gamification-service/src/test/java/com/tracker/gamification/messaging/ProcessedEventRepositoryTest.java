package com.tracker.gamification.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ProcessedEventRepositoryTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void existsById_falseBeforeSave_trueAfterSave() {
        assertFalse(processedEventRepository.existsById("logId-100"));

        processedEventRepository.save(new ProcessedEvent("logId-100", LocalDateTime.now()));

        assertTrue(processedEventRepository.existsById("logId-100"));
    }

    @Test
    void existsById_isKeyedOnIdempotencyKey_notAnUnrelatedId() {
        processedEventRepository.save(new ProcessedEvent("logId-1", LocalDateTime.now()));

        assertFalse(processedEventRepository.existsById("logId-2"));
    }

    // Issue #82: without ProcessedEvent implementing Persistable with isNew() hardcoded true,
    // Spring Data's default isNew() (id == null) is always false for this manually-assigned
    // String @Id, so save() compiles to em.merge() -- a second save of the same key silently
    // becomes an UPDATE instead of throwing. This is the regression test for that: it fails on
    // the pre-fix code (no exception, silent update) and passes once isNew() is hardcoded true,
    // which forces em.persist() and lets the unique PK reject the real duplicate.
    @Test
    void saveAndFlush_sameKeyTwice_throwsInsteadOfSilentlyUpdating() {
        processedEventRepository.saveAndFlush(new ProcessedEvent("logId-200", LocalDateTime.now()));

        assertThrows(DataIntegrityViolationException.class,
                () -> processedEventRepository.saveAndFlush(new ProcessedEvent("logId-200", LocalDateTime.now())));
    }
}
