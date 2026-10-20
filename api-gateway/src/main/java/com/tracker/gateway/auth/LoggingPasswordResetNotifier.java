package com.tracker.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Default {@link PasswordResetNotifier}: nothing is sent. By default it logs only that a reset was
 * issued. With {@code app.password-reset.log-token=true} it also logs the token, so the flow can be
 * exercised locally without a mail server. Never enable that outside development: a logged token is
 * a working account takeover link.
 */
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final boolean logToken;

    public LoggingPasswordResetNotifier(boolean logToken) {
        this.logToken = logToken;
    }

    @Override
    public void sendResetToken(String email, String firstName, String token, Instant expiresAt) {
        if (logToken) {
            log.info("Password reset issued for {} (expires {}). Token: {}", email, expiresAt, token);
        } else {
            log.info("Password reset issued (expires {}); no delivery channel is configured", expiresAt);
        }
    }
}
