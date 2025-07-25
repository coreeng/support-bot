package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.client.SlackClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
@EnableConfigurationProperties({
        SupportTeamProps.class,
        StaticSupportTeamProps.class
})
@RequiredArgsConstructor
public class SupportTeamConfig {
    private final SupportTeamProps supportTeamProps;
    private final StaticSupportTeamProps staticSupportTeamProps;

    @Bean
    @ConditionalOnProperty(value = "support-team.static.enabled", havingValue = "false", matchIfMissing = true)
    public SupportMemberFetcher slackSupportMemberUpdater(SlackClient slackClient, ExecutorService executor) {
        return new SlackSupportMemberFetcher(slackClient, executor);
    }

    @Bean
    @ConditionalOnProperty("support-team.static.enabled")
    public SupportMemberFetcher staticSupportMemberUpdater() {
        return new StaticSupportMemberFetcher(staticSupportTeamProps);
    }

    @Bean
    public SupportTeamService supportTeamService(SupportMemberFetcher memberUpdater) {
        return new SupportTeamService(supportTeamProps, memberUpdater);
    }
}