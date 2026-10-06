package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
class TestKitLauncherSessionStoreTest {
    private Config config;

    @Test
    void injectsLauncherSessionValuesIntoFieldsAndParameters(TestKit testKit, SlackWiremock slackWiremock) {
        assertThat(config).isSameAs(testKit.config());
        assertThat(slackWiremock.port()).isPositive();
    }
}
