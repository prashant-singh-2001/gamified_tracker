package com.tracker.gateway.auth;

import java.time.Instant;

/**
 * Delivery port for password-reset tokens. The gateway decides when a reset is issued; how the
 * token reaches the user (email, SMS, ...) is whatever implementation is registered. The shipped
 * default only logs, because no mail provider is configured. Define your own bean of this type to
 * replace it.
 *
 * <p>Implementations should hand the work off quickly (queue, async): {@code /auth/forgot} must
 * answer the same way whether or not the address belongs to an account, and a slow send would
 * reveal that. A thrown exception is caught and logged by the caller for the same reason.
 */
public interface PasswordResetNotifier {

    /**
     * @param email     the account's address
     * @param firstName the account holder's first name, for the message greeting
     * @param token     the raw, single-use token to put in the reset link (never stored server-side)
     * @param expiresAt when the token stops working
     */
    void sendResetToken(String email, String firstName, String token, Instant expiresAt);
}
