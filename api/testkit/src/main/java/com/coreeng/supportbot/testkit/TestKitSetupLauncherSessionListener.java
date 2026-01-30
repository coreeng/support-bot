package com.coreeng.supportbot.testkit;

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
    static final Namespace namespace = Namespace.create(TestKitExtension.class);
    private static final Logger logger = LoggerFactory.getLogger(TestKitSetupLauncherSessionListener.class);
    private static final String CONFIG_FILE = "config.yaml";
    private TestKit testKit;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            Config config = Config.load(CONFIG_FILE);
            testKit = TestKit.create(config);
            testKit.slack().wiremock().start();
            logger.info("SlackWiremock server started successfully");

            session.getStore().put(namespace, SlackWiremock.class, testKit.slack().wiremock());
            session.getStore().put(namespace, Config.class, config);
            session.getStore().put(namespace, SupportBotClient.class, testKit.supportBotClient());
            session.getStore().put(namespace, TestKit.class, testKit);
        } catch (RuntimeException e) {
            logger.error("Failed initialising tests", e);
            throw e;
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (testKit == null) {
            logger.warn("TestKit was not initialized; nothing to shut down");
            return;
        }

        logger.info("Closing SlackWiremock server");
        try {
            testKit.slack().wiremock().stop();
        } catch (RuntimeException e) {
            logger.warn("Failed to stop SlackWiremock server cleanly", e);
        }
    }
}
