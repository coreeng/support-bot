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
        SupportLeadershipTeamProps.class,
        StaticSupportTeamProps.class,
        StaticLeadershipTeamProps.class
})
@RequiredArgsConstructor
public class SupportTeamConfig {
    private final SupportTeamProps supportTeamProps;
    private final SupportLeadershipTeamProps leadershipTeamProps;
    private final StaticSupportTeamProps staticSupportProps;
    private final StaticLeadershipTeamProps staticLeadershipProps;

    @Bean
    @ConditionalOnProperty(value = "team.support.static.enabled", havingValue = "false", matchIfMissing = true)
    public SupportMemberFetcher slackSupportMemberFetcher(SlackClient slackClient, ExecutorService executor) {
        return new SlackSupportMemberFetcher(slackClient, executor);
    }

    @Bean
    @ConditionalOnProperty("team.support.static.enabled")
    public SupportMemberFetcher staticSupportMemberFetcher() {
        return new StaticSupportMemberFetcher(staticSupportProps, staticLeadershipProps);
    }

    @Bean
    public SupportTeamService supportTeamService(SupportMemberFetcher memberFetcher) {
        return new SupportTeamService(supportTeamProps, leadershipTeamProps, memberFetcher);
    }
}