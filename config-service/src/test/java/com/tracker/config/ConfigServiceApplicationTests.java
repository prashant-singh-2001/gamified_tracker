package com.tracker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("native")
@DisplayName("Config Service Context Test")
class ConfigServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
