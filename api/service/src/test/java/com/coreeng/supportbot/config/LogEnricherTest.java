package com.coreeng.supportbot.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogEnricherTest {

    @Test
    void shouldReturnFormattedKeyValueWhenEnabled() {
        var props = new MetricsLoggingProps(true, "support_bot_ticket_");
        var logEnricher = new LogEnricher(props);

        String result = logEnricher.kv("id", 123);

        assertEquals("support_bot_ticket_id=123", result);
    }

    @Test
    void shouldReturnEmptyStringWhenDisabled() {
        var props = new MetricsLoggingProps(false, "support_bot_ticket_");
        var logEnricher = new LogEnricher(props);

        String result = logEnricher.kv("id", 123);

        assertEquals("", result);
    }

    @Test
    void shouldHandleNullValue() {
        var props = new MetricsLoggingProps(true, "prefix_");
        var logEnricher = new LogEnricher(props);

        String result = logEnricher.kv("team", null);

        assertEquals("prefix_team=null", result);
    }

    @Test
    void shouldWorkWithEmptyPrefix() {
        var props = new MetricsLoggingProps(true, "");
        var logEnricher = new LogEnricher(props);

        String result = logEnricher.kv("id", 42);

        assertEquals("id=42", result);
    }
}
