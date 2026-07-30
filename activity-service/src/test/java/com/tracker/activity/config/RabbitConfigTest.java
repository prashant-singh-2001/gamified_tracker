package com.tracker.activity.config;

import com.tracker.contracts.event.ActivityLoggedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("RabbitConfig")
class RabbitConfigTest {

    @Test
    @DisplayName("producer stamps the stable logical __TypeId__, not the FQCN")
    void stampsStableTypeId() {
        var typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setIdClassMapping(Map.of(ActivityLoggedEvent.TYPE_ID, ActivityLoggedEvent.class));

        var props = new MessageProperties();
        typeMapper.fromClass(ActivityLoggedEvent.class, props);

        assertEquals("activity.logged", props.getHeaders().get("__TypeId__"));
        assertNotEquals(ActivityLoggedEvent.class.getName(), props.getHeaders().get("__TypeId__"));
    }
}
