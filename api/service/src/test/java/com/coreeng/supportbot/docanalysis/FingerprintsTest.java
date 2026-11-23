package com.coreeng.supportbot.docanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprintsTest {

    @Test
    void isStableForTheSameParts() {
        assertThat(Fingerprints.of("github", "prompt", "2")).isEqualTo(Fingerprints.of("github", "prompt", "2"));
        assertThat(Fingerprints.of("github")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void changesWhenAnyPartChanges() {
        assertThat(Fingerprints.of("github", "prompt", "2")).isNotEqualTo(Fingerprints.of("github", "prompt", "3"));
    }

    @Test
    void doesNotConfuseHowThePartsAreSplit() {
        assertThat(Fingerprints.of("ab", "c")).isNotEqualTo(Fingerprints.of("a", "bc"));
        assertThat(Fingerprints.of("a", "")).isNotEqualTo(Fingerprints.of("a"));
    }
}
