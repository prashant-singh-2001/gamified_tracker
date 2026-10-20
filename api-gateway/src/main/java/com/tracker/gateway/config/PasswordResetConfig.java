package com.tracker.gateway.config;

import com.tracker.gateway.auth.LoggingPasswordResetNotifier;
import com.tracker.gateway.auth.PasswordResetNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {

    // Replaced by any other PasswordResetNotifier bean (a real mail sender).
    @Bean
    @ConditionalOnMissingBean(PasswordResetNotifier.class)
    public PasswordResetNotifier loggingPasswordResetNotifier(PasswordResetProperties props) {
        return new LoggingPasswordResetNotifier(props.logToken());
    }
}
