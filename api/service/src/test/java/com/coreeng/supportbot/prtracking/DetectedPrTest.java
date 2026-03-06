package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DetectedPrTest {

    @Test
    void rejectsZeroPullNumber() {
        assertThatThrownBy(() -> new DetectedPr("my-org/repo", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pullNumber must be greater than 0");
    }

    @Test
    void rejectsNegativePullNumber() {
        assertThatThrownBy(() -> new DetectedPr("my-org/repo", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pullNumber must be greater than 0");
    }
}
