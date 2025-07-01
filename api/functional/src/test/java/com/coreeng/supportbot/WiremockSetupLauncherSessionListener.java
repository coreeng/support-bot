package com.coreeng.supportbot;

import com.coreeng.supportbot.wiremock.WiremockManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.platform.engine.support.store.Namespace;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

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

            waitForHealthyDeployment();
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

    private void waitForHealthyDeployment() {
        await()
            .atMost(Duration.ofMinutes(5))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                wiremockManager.checkForUnmatchedRequests();
                try {
                    return given()
                               .when()
                               .get("http://localhost:8081/health")
                               .then()
                               .extract()
                               .statusCode() == 200;
                } catch (Exception e) {
                    if (e.getMessage().contains("Connection refused")) {
                        logger.info("Waiting for support bot to be ready");
                        return false;
                    } else {
                        logger.error("Failed to check external services health", e);
                        throw new RuntimeException(e);
                    }
                }
            });
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
