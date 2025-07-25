package com.coreeng.supportbot;

import com.coreeng.supportbot.wiremock.WiremockManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.platform.engine.support.store.Namespace;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This listener is picked up because it specified in resources/META-INF/services
 * It initializes and closes Wiremock instances for external services.
 */
public class WiremockSetupLauncherSessionListener implements LauncherSessionListener {
    private static final Logger logger = LoggerFactory.getLogger(WiremockSetupLauncherSessionListener.class);
    private static final String CONFIG_FILE = "config.yaml";

    private WiremockManager wiremockManager;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            var config = readConfigurationFile();
            wiremockManager = new WiremockManager(config);
            wiremockManager.startAll();
            logger.info("Wiremock servers for external services started successfully");
        } catch (RuntimeException e) {
            logger.error("Failed initialising tests", e);
            throw e;
        }
        session.getStore().put(Namespace.GLOBAL, WiremockManager.class, wiremockManager);
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        logger.info("Closing Wiremock servers for external services");
        wiremockManager.stopAll();
    }


    private Config readConfigurationFile() {
        try {
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.findAndRegisterModules();

            var configUrl = Thread.currentThread().getContextClassLoader().getResource(CONFIG_FILE);
            return objectMapper.readValue(configUrl, Config.class);
        } catch (IOException e) {
            logger.error("Failed to read configuration file {}", CONFIG_FILE, e);
            throw new RuntimeException(e);
        }
    }
}
