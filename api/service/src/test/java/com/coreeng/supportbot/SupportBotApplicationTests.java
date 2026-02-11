package com.coreeng.supportbot;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.coreeng.supportbot.slack.client.SlackClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits")
class SupportBotApplicationTests {

    @Test
    void contextLoads() {
        assertTrue(true);
    }

    @Configuration
    static class Config {
        @Bean
        SlackClient slackClient() {
            return mock(SlackClient.class);
        }
    }
}
