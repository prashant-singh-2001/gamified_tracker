package com.tracker.gamification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RankingProperties.class)
public class RankingConfig {

    // Zone-less timestamps are UTC by convention across the services, so the clock is UTC too: the
    // decay compares "now" with session times that are UTC wall-clock values.
    @Bean
    public Clock rankingClock() {
        return Clock.systemUTC();
    }
}
