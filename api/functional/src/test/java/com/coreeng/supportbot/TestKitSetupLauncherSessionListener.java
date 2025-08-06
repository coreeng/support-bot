package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.SupportBotSlackClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.wiremock.AzureWiremock;
import com.coreeng.supportbot.wiremock.GcpWiremock;
import com.coreeng.supportbot.wiremock.KubernetesWiremock;
import com.coreeng.supportbot.wiremock.SlackWiremock;
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
 * It initializes Wiremocks and TestKits.
 */
public class TestKitSetupLauncherSessionListener implements LauncherSessionListener {
    static final Namespace namespace = Namespace.create(TestKitExtension.class);
    private static final Logger logger = LoggerFactory.getLogger(TestKitSetupLauncherSessionListener.class);
    private static final String CONFIG_FILE = "config.yaml";
    private WiremockManager wiremockManager;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Config config;
        try {
            config = readConfigurationFile();
            wiremockManager = new WiremockManager(config);
            wiremockManager.startAll();
            logger.info("Wiremock servers for external services started successfully");
        } catch (RuntimeException e) {
            logger.error("Failed initialising tests", e);
            throw e;
        }
        session.getStore().put(namespace, WiremockManager.class, wiremockManager);
        session.getStore().put(namespace, SlackWiremock.class, wiremockManager.slackWiremock);
        session.getStore().put(namespace, KubernetesWiremock.class, wiremockManager.kubernetesWiremock);
        session.getStore().put(namespace, GcpWiremock.class, wiremockManager.gcpWiremock);
        session.getStore().put(namespace, AzureWiremock.class, wiremockManager.azureWiremock);
        session.getStore().put(namespace, Config.class, config);
        var supportBotClient= new SupportBotClient(config.supportBot().baseUrl(), wiremockManager.slackWiremock);
        var supportBotSlackClient = new SupportBotSlackClient(config, wiremockManager.slackWiremock);
        session.getStore().put(namespace, SupportBotClient.class, supportBotClient);
        session.getStore().put(namespace, SupportBotSlackClient.class, supportBotSlackClient);
        session.getStore().put(namespace, TestKit.class, new TestKit(wiremockManager, supportBotSlackClient, supportBotClient, config));
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
