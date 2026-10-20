package com.tracker.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param tokenTtl how long an issued reset token works (default 30 minutes)
 * @param logToken dev only: have the default notifier write the raw token to the log
 */
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(Duration tokenTtl, boolean logToken) {

    public PasswordResetProperties {
        if (tokenTtl == null) {
            tokenTtl = Duration.ofMinutes(30);
        }
    }
}
