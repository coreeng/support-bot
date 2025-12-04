package com.coreeng.supportbot.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoggingConfigurationTest {

    @Test
    void shouldParseJsonLoggingConfiguration() {
        assertDoesNotThrow(() -> loadLogbackConfig("logback-json.xml"));
    }

    @Test
    void shouldParsePlainTextLoggingConfiguration() {
        assertDoesNotThrow(() -> loadLogbackConfig("logback.xml"));

    }

    private void loadLogbackConfig(String configFile) throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        context.reset();
        configurator.doConfigure(getClass().getClassLoader().getResourceAsStream(configFile));
    }
}
