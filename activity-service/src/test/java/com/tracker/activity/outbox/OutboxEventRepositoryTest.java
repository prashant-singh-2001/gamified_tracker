package com.tracker.activity.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private OutboxEvent row(Long aggregateId, LocalDateTime createdAt, LocalDateTime publishedAt) {
        return OutboxEvent.builder()
                .aggregateType("ActivityLog")
                .aggregateId(aggregateId)
                .eventType("ActivityLogged")
                .payload("{}")
                .idempotencyKey(String.valueOf(aggregateId))
                .createdAt(createdAt)
                .publishedAt(publishedAt)
                .build();
    }

    @Test
    void findTop100ByPublishedAtIsNullOrderByCreatedAtAsc_returnsOnlyUnpublishedOldestFirst() {
        LocalDateTime now = LocalDateTime.now();

        outboxEventRepository.save(row(1L, now.minusMinutes(5), null));      // unpublished, oldest
        outboxEventRepository.save(row(2L, now, LocalDateTime.now()));       // already published — excluded
        outboxEventRepository.save(row(3L, now.minusMinutes(1), null));      // unpublished, newer than #1

        List<OutboxEvent> pending = outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        assertEquals(2, pending.size());
        assertTrue(pending.stream().allMatch(e -> e.getPublishedAt() == null));
        assertEquals(1L, pending.get(0).getAggregateId());
        assertEquals(3L, pending.get(1).getAggregateId());
    }

    @Test
    void findTop100ByPublishedAtIsNullOrderByCreatedAtAsc_emptyWhenAllPublished() {
        outboxEventRepository.save(row(4L, LocalDateTime.now(), LocalDateTime.now()));

        List<OutboxEvent> pending = outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        assertTrue(pending.isEmpty());
    }
}
