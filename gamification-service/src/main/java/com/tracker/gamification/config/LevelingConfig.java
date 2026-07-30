package com.tracker.gamification.config;

import com.tracker.gamification.domain.LevelCurve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LevelingConfig {

    @Bean
    public LevelCurve defaultLevelCurve(
            @Value("${leveling.default-curve.base-xp:100.0}") double baseXp,
            @Value("${leveling.default-curve.exponent:1.5}") double exponent,
            @Value("${leveling.default-curve.max-level:100}") int maxLevel,
            @Value("${leveling.default-curve.enabled:true}") boolean enabled) {
        return new LevelCurve(baseXp, exponent, maxLevel, enabled);
    }
}
