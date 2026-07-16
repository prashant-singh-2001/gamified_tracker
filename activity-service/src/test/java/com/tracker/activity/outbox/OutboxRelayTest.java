package com.tracker.activity.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.messaging.ActivityLoggedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Relay Tests")
class OutboxRelayTest {

    private static final String EXCHANGE = "activity.events";
    private static final String ROUTING_KEY = "activity.logged";

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, rabbitTemplate, objectMapper, EXCHANGE, ROUTING_KEY);
    }

    private OutboxEvent unpublishedRow(Long id) throws Exception {
        var event = new ActivityLoggedEvent(id, 1L, 2L, 30.0);
        return OutboxEvent.builder()
                .id(id)
                .aggregateType("ActivityLog")
                .aggregateId(id)
                .eventType("ActivityLogged")
                .payload(objectMapper.writeValueAsString(event))
                .idempotencyKey(String.valueOf(id))
                .createdAt(LocalDateTime.now())
                .publishedAt(null)
                .build();
    }

    @Test
    @DisplayName("publishes unpublished rows and stamps publishedAt on success")
    void publishPending_publishesAndStampsSuccess() throws Exception {
        OutboxEvent row = unpublishedRow(1L);
        when(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(row));

        relay.publishPending();

        ArgumentCaptor<ActivityLoggedEvent> captor = ArgumentCaptor.forClass(ActivityLoggedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
        assertEquals(1L, captor.getValue().logId());
        assertNotNull(row.getPublishedAt(), "publishedAt is stamped only after a successful send");
    }

    @Test
    @DisplayName("leaves publishedAt null when the send fails, so it's retried next tick")
    void publishPending_leavesUnpublishedOnSendFailure() throws Exception {
        OutboxEvent row = unpublishedRow(2L);
        when(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(row));
        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ActivityLoggedEvent.class));

        assertDoesNotThrow(() -> relay.publishPending(), "a single failed row must not blow up the scheduled poll");

        assertNull(row.getPublishedAt(), "left unpublished so it is retried on the next tick");
    }

    @Test
    @DisplayName("does nothing when there are no unpublished rows")
    void publishPending_noRows_noInteractionsWithRabbit() {
        when(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

        relay.publishPending();

        verifyNoInteractions(rabbitTemplate);
    }
}
