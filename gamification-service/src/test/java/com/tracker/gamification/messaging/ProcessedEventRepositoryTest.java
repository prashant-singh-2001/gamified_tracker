package com.tracker.gamification.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
