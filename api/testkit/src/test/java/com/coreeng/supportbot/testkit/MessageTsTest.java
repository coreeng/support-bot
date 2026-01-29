package com.coreeng.supportbot.testkit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTsTest {

    @Test
    void toStringRoundTripsSlackStyleTimestamp() {
        MessageTs ts = MessageTs.fromTsString("1234567890.123456");
        assertThat(ts.toString()).isEqualTo("1234567890.123456");
    }

    @Test
    void fromTsStringHandlesNineDigitFractionFromOldFormat() {
        MessageTs ts = MessageTs.fromTsString("1234567890.123456000");
        assertThat(ts.toString()).isEqualTo("1234567890.123456");
    }

    @Test
    void fromTsStringHandlesWholeSeconds() {
        MessageTs ts = MessageTs.fromTsString("1234567890");
        assertThat(ts.toString()).isEqualTo("1234567890.000000");
    }

    @Test
    void nowProducesUniqueSlackFormattedTimestamps() {
        int count = 1000;
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(MessageTs.now().toString());
        }

        assertThat(new HashSet<>(values)).hasSize(count);
        for (String value : values) {
            assertThat(value).matches("\\d+\\.\\d{6}");
        }
    }

    @Test
    void nowIntUsesGivenCounterValueInSuffix() {
        MessageTs ts0 = MessageTs.now(0);
        MessageTs ts1 = MessageTs.now(1);

        String[] parts0 = ts0.toString().split("\\.");
        String[] parts1 = ts1.toString().split("\\.");

        assertThat(parts0[1]).isEqualTo("000000");
        assertThat(parts1[1]).isEqualTo("000001");
    }
}

