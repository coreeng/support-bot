package com.coreeng.supportbot.testkit;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import org.junit.platform.engine.support.store.Namespace;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This listener is picked up because it specified in resources/META-INF/services
 * It initializes Wiremock server and TestKits.
 */
public class TestKitSetupLauncherSessionListener implements LauncherSessionListener {
    static final Namespace NAMESPACE = Namespace.create(TestKitExtension.class);
    private static final Logger LOGGER = LoggerFactory.getLogger(TestKitSetupLauncherSessionListener.class);
    private static final String CONFIG_FILE = "config.yaml";
    private TestKit testKit;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            Config config = Config.load(CONFIG_FILE);
            testKit = TestKit.create(config);
            testKit.slack().wiremock().start();
            LOGGER.info("SlackWiremock server started successfully");

            // Configure RestAssured with default auth bypass headers
            RestAssured.requestSpecification = new RequestSpecBuilder()
                    .addHeader("X-Test-User", "test@functional.test")
                    .addHeader("X-Test-Role", "support")
                    .build();
            LOGGER.info("RestAssured configured with test auth bypass headers");

            session.getStore()
                    .put(NAMESPACE, SlackWiremock.class, testKit.slack().wiremock());
            session.getStore().put(NAMESPACE, Config.class, config);
            session.getStore().put(NAMESPACE, SupportBotClient.class, testKit.supportBotClient());
            session.getStore().put(NAMESPACE, TestKit.class, testKit);
        } catch (RuntimeException e) {
            LOGGER.error("Failed initialising tests", e);
            throw e;
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (testKit == null) {
            LOGGER.warn("TestKit was not initialized; nothing to shut down");
            return;
        }

        LOGGER.info("Closing SlackWiremock server");
        try {
            testKit.slack().wiremock().stop();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to stop SlackWiremock server cleanly", e);
        }
    }
}
