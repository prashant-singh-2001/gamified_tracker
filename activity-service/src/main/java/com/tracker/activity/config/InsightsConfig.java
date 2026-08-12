package com.tracker.activity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Narrator beans (WeeklyDigestNarrator) are added here in a later phase, once the domain/ seam
// (issue #65) exists to build them against. Mirrors AdminBootstrapConfig: a pure-properties
// registrar until there's a bean to declare.
@Configuration
@EnableConfigurationProperties(InsightsProperties.class)
public class InsightsConfig {
}
