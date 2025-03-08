package com.coreeng.supportbot;

import com.coreeng.supportbot.slack.client.SlackClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringBootTest
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
