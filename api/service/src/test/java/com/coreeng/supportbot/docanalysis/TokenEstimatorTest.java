package com.coreeng.supportbot.docanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenEstimatorTest {

    @Test
    void countsFourCharactersATokenRoundingUp() {
        assertThat(TokenEstimator.estimate("")).isZero();
        assertThat(TokenEstimator.estimate("a")).isEqualTo(1);
        assertThat(TokenEstimator.estimate("abcd")).isEqualTo(1);
        assertThat(TokenEstimator.estimate("abcde")).isEqualTo(2);
    }
}
