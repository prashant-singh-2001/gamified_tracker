package com.tracker.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitPropertiesTest {

    @Test
    void bindsNestedBucketsFromFlatProperties() {
        Map<String, String> source = Map.of(
                "rate-limit.activity.capacity", "100",
                "rate-limit.activity.period-seconds", "60",
                "rate-limit.gamification.capacity", "50",
                "rate-limit.gamification.period-seconds", "30",
                "rate-limit.auth.capacity", "10",
                "rate-limit.auth.period-seconds", "60"
        );

        RateLimitProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("rate-limit", RateLimitProperties.class)
                .get();

        assertEquals(100, props.activity().capacity());
        assertEquals(60, props.activity().periodSeconds());
        assertEquals(50, props.gamification().capacity());
        assertEquals(30, props.gamification().periodSeconds());
        assertEquals(10, props.auth().capacity());
        assertEquals(60, props.auth().periodSeconds());
    }
}
