package com.tracker.gateway.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailsTest {

    @Test
    void trimsAndLowerCases() {
        assertThat(Emails.normalize("  Ada.Lovelace@Example.COM \t")).isEqualTo("ada.lovelace@example.com");
    }

    @Test
    void isIdempotent() {
        String once = Emails.normalize(" A@B.Com ");
        assertThat(Emails.normalize(once)).isEqualTo(once);
    }

    @Test
    void nullStaysNullSoValidationCanRejectIt() {
        assertThat(Emails.normalize(null)).isNull();
    }

    @Test
    void lowerCasingIsLocaleIndependent() {
        // Turkish dotless-i: a default-locale toLowerCase() would turn "I" into "ı" and break
        // matching against stored emails on a Turkish-locale JVM.
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(Emails.normalize("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
