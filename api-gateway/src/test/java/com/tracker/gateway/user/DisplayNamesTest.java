package com.tracker.gateway.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNamesTest {

    @Test
    void firstNamePlusLastInitial() {
        assertThat(DisplayNames.of("Ada", "Lovelace")).isEqualTo("Ada L.");
    }

    @Test
    void initialIsUpperCasedWhateverTheStoredCase() {
        assertThat(DisplayNames.of("ada", "lovelace")).isEqualTo("ada L.");
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(DisplayNames.of("  Ada ", "  Lovelace ")).isEqualTo("Ada L.");
    }

    @Test
    void noLastNameMeansJustTheFirstName() {
        assertThat(DisplayNames.of("Ada", null)).isEqualTo("Ada");
        assertThat(DisplayNames.of("Ada", "   ")).isEqualTo("Ada");
    }

    @Test
    void noUsableFirstNameMeansNoDisplayName() {
        assertThat(DisplayNames.of(null, "Lovelace")).isNull();
        assertThat(DisplayNames.of("  ", "Lovelace")).isNull();
    }

    @Test
    void accentedAndSupplementaryPlaneInitialsAreNotMangled() {
        assertThat(DisplayNames.of("Émile", "étienne")).isEqualTo("Émile É.");
        // U+1D4D0 (mathematical bold script capital A) is outside the BMP: a char-based substring
        // would leave a lone surrogate.
        assertThat(DisplayNames.of("Ada", "𝓐bc")).isEqualTo("Ada 𝓐.");
    }

    @Test
    void neverIncludesMoreOfTheLastNameThanTheInitial() {
        assertThat(DisplayNames.of("Ada", "Lovelace")).doesNotContain("ovelace");
    }
}
