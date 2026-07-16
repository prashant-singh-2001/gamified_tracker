package com.tracker.activity.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;   // "ActivityLog"
    private Long aggregateId;       // = logId
    private String eventType;       // "ActivityLogged"

    @Column(columnDefinition = "text")
    private String payload;         // JSON of ActivityLoggedEvent

    @Column(unique = true)
    private String idempotencyKey;  // = logId (string)

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;  // null until the relay publishes it
}