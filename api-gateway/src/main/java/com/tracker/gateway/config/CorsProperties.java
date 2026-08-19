package com.tracker.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins, List<String> allowedMethods,
                             List<String> allowedHeaders, long maxAgeSeconds) {
}
