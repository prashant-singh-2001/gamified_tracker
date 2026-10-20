package com.tracker.gateway.user;

import java.util.Locale;

/**
 * Emails are stored and compared trimmed and lower-cased, so "Ada@Example.com " and
 * "ada@example.com" are the same account at registration and at login. Normalising lives in one
 * place so the request DTOs, the admin bootstrap and the migration that backfilled existing rows
 * (V3) cannot drift apart.
 */
public final class Emails {

    private Emails() {
    }

    /** Trims and lower-cases; {@code null} stays {@code null} so Bean Validation can reject it. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
