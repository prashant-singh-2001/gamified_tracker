package com.tracker.activity.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.activity.messaging.ActivityLoggedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                       ObjectMapper objectMapper,
                       @Value("${messaging.exchange}") String exchange,
                       @Value("${messaging.routing-key}") String routingKey) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
    @Transactional
    public void publishPending() {
        var batch = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (var row : batch) {
            try {
                var event = objectMapper.readValue(row.getPayload(), ActivityLoggedEvent.class);
                rabbitTemplate.convertAndSend(exchange, routingKey, event);
                row.setPublishedAt(LocalDateTime.now());   // stamped only on success
            } catch (Exception e) {
                // leave publishedAt null -> retried next tick (at-least-once)
                log.warn("Failed to publish outbox row {} (will retry)", row.getId(), e);
            }
        }
    }
}