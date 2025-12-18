package com.coreeng.supportbot.config;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackClientImpl;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.jakarta_socket_mode.impl.JakartaSocketModeClientTyrusImpl;
import com.slack.api.util.thread.DaemonThreadFactory;
import com.slack.api.util.thread.ExecutorServiceProvider;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Profile("!test")
public class SlackAppConfig {
    @Bean
    public App slackApp(SlackProps slackProps) {
        AppConfig config = new AppConfig();
        config.setSingleTeamBotToken(slackProps.creds().token());
        config.setSigningSecret(slackProps.creds().signingSecret());
        config.setExecutorServiceProvider(new ConcurrentExecutorServiceProvider());
        config.setRequestVerificationEnabled(slackProps.enableRequestVerification());
        // Auto-acknowledge all events that don't have explicit handlers to prevent
        // "no handler found" warnings from SocketModeApp for events we don't care about
        config.setAllEventsApiAutoAckEnabled(true);
        if (Strings.isNotBlank(slackProps.client().methodsBaseUrl())) {
            config.getSlack().getConfig().setMethodsEndpointUrlPrefix(slackProps.client().methodsBaseUrl());
            config.getSlack().getConfig().setPrettyResponseLoggingEnabled(true);
        }
        return new App(config);
    }

    @Bean
    @ConditionalOnProperty(
        value = "slack.mode",
        havingValue = "http"
    )
    public ServletRegistrationBean<SlackHttpController> slackHttpController(App app) {
        return new ServletRegistrationBean<>(
            new SlackHttpController(app),
            "/slack/events"
        );
    }

    @Bean
    @ConditionalOnProperty(
        value = "slack.mode",
        havingValue = "socket"
    )
    public SlackSocketController slackSocketController(
        App app,
        SlackProps slackCreds,
        MeterRegistry meterRegistry
    ) throws IOException {
        return new SlackSocketController(app, slackCreds, meterRegistry);
    }

    @Bean
    public SlackClient slackClient(App slackApp,
                                   @Qualifier("permalink-cache") Cache permalinkCache,
                                   @Qualifier("slack-user-cache") Cache userCache,
                                   @Qualifier("slack-group-cache") Cache groupCache,
                                   @Qualifier("slack-channel-cache") Cache channelCache,
                                   MeterRegistry meterRegistry) {
        return new SlackClientImpl(slackApp.client(), permalinkCache, userCache, groupCache, channelCache, meterRegistry);
    }

    /**
     * In the original implementation it creates scheduled executor with single thread which leads to
     * no concurrency in message processing in socket mode.
     * This implementation sets the default concurrency value as number of threads for scheduled executor.
     */
    private static class ConcurrentExecutorServiceProvider implements ExecutorServiceProvider {
        @Override
        public ExecutorService createThreadPoolExecutor(String threadGroupName, int poolSize) {
            return Executors.newFixedThreadPool(poolSize, new DaemonThreadFactory(threadGroupName));
        }

        @Override
        public ScheduledExecutorService createThreadScheduledExecutor(String threadGroupName) {
            return Executors.newScheduledThreadPool(
                JakartaSocketModeClientTyrusImpl.DEFAULT_MESSAGE_PROCESSOR_CONCURRENCY,
                new DaemonThreadFactory(threadGroupName)
            );
        }
    }
}
