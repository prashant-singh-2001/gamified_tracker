package com.tracker.gateway.user;

/**
 * How a user is shown to OTHER users on a leaderboard: first name plus the last initial
 * ("Ada Lovelace" -> "Ada L."). Never the email, never the full last name. One place, so the
 * leaderboard enrichment and any future surface cannot disagree about what is safe to expose.
 */
public final class DisplayNames {

    private DisplayNames() {
    }

    /** @return the display name, or {@code null} if there is no usable first name. */
    public static String of(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty()) {
            return null;
        }
        if (last.isEmpty()) {
            return first;
        }
        // Code point, not char: a supplementary-plane letter must not be split into a lone surrogate.
        int initial = Character.toUpperCase(last.codePointAt(0));
        return first + " " + new String(Character.toChars(initial)) + ".";
    }
}
