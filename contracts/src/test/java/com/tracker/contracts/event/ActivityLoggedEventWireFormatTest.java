package com.tracker.contracts.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityLoggedEvent wire format")
class ActivityLoggedEventWireFormatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("serializes to exactly the four agreed field names")
    void serializesToAgreedFieldNames() throws Exception {
        var json = mapper.readTree(
                mapper.writeValueAsString(new ActivityLoggedEvent(42L, 1L, 2L, 30.0)));

        // Renaming or dropping any of these silently breaks in-flight messages AND every
        // unpublished outbox_event.payload row, which stores this same JSON.
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("logId", "userId", "activityId", "xpEarned"), fields);

        assertEquals(42L, json.get("logId").asLong());
        assertEquals(1L, json.get("userId").asLong());
        assertEquals(2L, json.get("activityId").asLong());
        assertEquals(30.0, json.get("xpEarned").asDouble());
    }

    @Test
    @DisplayName("deserializes untyped JSON — the outbox_event.payload shape")
    void deserializesUntypedJson() throws Exception {
        // Exactly what OutboxRelay reads back out of the outbox table: no @class, no type header.
        var event = mapper.readValue(
                "{\"logId\":42,\"userId\":1,\"activityId\":2,\"xpEarned\":30.0}",
                ActivityLoggedEvent.class);

        assertEquals(42L, event.logId());
        assertEquals(1L, event.userId());
        assertEquals(2L, event.activityId());
        assertEquals(30.0, event.xpEarned());
    }

    @Test
    @DisplayName("TYPE_ID is a constant, not a serialized field")
    void typeIdIsNotSerialized() throws Exception {
        var json = mapper.writeValueAsString(new ActivityLoggedEvent(1L, 1L, 1L, 0.0));

        assertFalse(json.contains("TYPE_ID"));
        assertFalse(json.contains("activity.logged"));
        assertEquals("activity.logged", ActivityLoggedEvent.TYPE_ID);
    }
}
