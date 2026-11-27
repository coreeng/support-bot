package com.coreeng.supportbot.docanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FingerprintTest {

    @Test
    void givesTheSameValueForTheSameParts() {
        assertThat(Fingerprint.of(List.of("github", "docs"))).isEqualTo(Fingerprint.of(List.of("github", "docs")));
    }

    @Test
    void givesDifferentValuesForDifferentSplitsOfTheSameCharacters() {
        assertThat(List.of(
                        Fingerprint.of(List.of("ab", "c")),
                        Fingerprint.of(List.of("a", "bc")),
                        Fingerprint.of(List.of("abc")),
                        Fingerprint.of(List.of("abc", "")),
                        Fingerprint.of(List.of("", "abc"))))
                .doesNotHaveDuplicates();
    }
}
