package com.tracker.contracts.event;

/**
 * The RabbitMQ message contract between activity-service (producer, via the transactional
 * outbox relay) and gamification-service (consumer). Previously hand-duplicated verbatim in
 * both services' messaging/ packages — see issue #23.
 *
 * The wire format is plain field-name JSON with no embedded type information. Renaming or
 * removing a component is therefore a BREAKING change for both in-flight messages AND every
 * unpublished outbox_event.payload row, which stores this exact JSON.
 * ActivityLoggedEventWireFormatTest guards the field names.
 *
 * occurredAtEpochMilli is the session end as UTC epoch milliseconds (activity timestamps are
 * zone-less and UTC by convention). It is what lets gamification age XP by when the session
 * happened rather than when the message was processed. It is nullable, and JSON without the field
 * (messages in flight, unpublished outbox rows) reads as null, so adding it is backward compatible.
 */
public record ActivityLoggedEvent(Long logId, Long userId, Long activityId, double xpEarned,
                                  Long occurredAtEpochMilli) {

    /** Without a session time; the consumer then falls back to the time it applied the XP. */
    public ActivityLoggedEvent(Long logId, Long userId, Long activityId, double xpEarned) {
        this(logId, userId, activityId, xpEarned, null);
    }

    /**
     * Stable logical name stamped into the AMQP __TypeId__ header, so the wire contract no
     * longer depends on this class's Java package. Before this module existed the header
     * carried com.tracker.activity.messaging.ActivityLoggedEvent — a class that never existed
     * on the consumer side. See both services' RabbitConfig.
     *
     * static, so Jackson does not serialize it as a field.
     */
    public static final String TYPE_ID = "activity.logged";
}
