package com.tracker.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;

// #61: no CORS config existed anywhere in the repo. Defaults to a closed perimeter (empty
// allowed-origins denies every cross-origin request) so this ships inert until a frontend
// opts in via CORS_ALLOWED_ORIGINS. Credentials are never used (auth is Authorization: Bearer,
// not cookies), so allowCredentials stays false — also sidesteps the allowCredentials=true +
// wildcard-origin incompatibility.
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(props.allowedOrigins());
        configuration.setAllowedMethods(props.allowedMethods());
        configuration.setAllowedHeaders(props.allowedHeaders());
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofSeconds(props.maxAgeSeconds()));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
