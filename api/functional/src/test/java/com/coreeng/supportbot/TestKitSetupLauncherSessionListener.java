package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.SupportBotSlackClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.SlackWiremock;
import org.junit.platform.engine.support.store.Namespace;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;


/**
 * This listener is picked up because it specified in resources/META-INF/services
 * It initializes Wiremock server and TestKits.
 */
public class TestKitSetupLauncherSessionListener implements LauncherSessionListener {
    static final Namespace namespace = Namespace.create(TestKitExtension.class);
    private static final Logger logger = LoggerFactory.getLogger(TestKitSetupLauncherSessionListener.class);
    private static final String configFile = "config.yaml";
    private SlackWiremock slackWiremock;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Config config;
        try {
            config = readConfigurationFileOnClasspath(configFile);
            slackWiremock = new SlackWiremock(config.mocks().slack());
            slackWiremock.start();
            logger.info("SlackWiremock server started successfully");
        } catch (RuntimeException e) {
            logger.error("Failed initialising tests", e);
            throw e;
        }
        session.getStore().put(namespace, SlackWiremock.class, slackWiremock);
        session.getStore().put(namespace, Config.class, config);
        var supportBotClient= new SupportBotClient(config.supportBot().baseUrl(), slackWiremock);
        var supportBotSlackClient = new SupportBotSlackClient(config, slackWiremock);
        session.getStore().put(namespace, SupportBotClient.class, supportBotClient);
        session.getStore().put(namespace, SupportBotSlackClient.class, supportBotSlackClient);
        session.getStore().put(namespace, TestKit.class, new TestKit(slackWiremock, supportBotSlackClient, supportBotClient, config));
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        logger.info("Closing SlackWiremock server");
        slackWiremock.stop();
    }

    private Config readConfigurationFileOnClasspath(String fileName) {
        try {
            var env = new StandardEnvironment();
            var yamlLoader = new YamlPropertySourceLoader();
            List<PropertySource<?>> yaml = yamlLoader.load("config", new ClassPathResource(fileName));
            yaml.forEach(ps -> env.getPropertySources().addLast(ps));
            var binder = new Binder(
                ConfigurationPropertySources.get(env),
                new PropertySourcesPlaceholdersResolver(env)
            );
            return binder.bind("", Config.class).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
