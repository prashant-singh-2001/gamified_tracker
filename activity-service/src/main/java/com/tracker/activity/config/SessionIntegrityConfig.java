package com.tracker.activity.config;

import com.tracker.activity.domain.DurationOutlierDetector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionIntegrityProperties.class)
public class SessionIntegrityConfig {

    @Bean
    public DurationOutlierDetector durationOutlierDetector(SessionIntegrityProperties properties) {
        return new DurationOutlierDetector(
                properties.modifiedZThreshold(), properties.minSamples(), properties.relativeFactor());
    }
}
