package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.client.SlackClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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
    @Qualifier("supportTeamFetcher")
    @ConditionalOnProperty(value = "team.support.static.enabled", havingValue = "false", matchIfMissing = true)
    public TeamMemberFetcher supportTeamFetcher(SlackClient slackClient, ExecutorService executor) {
        return new GenericSlackMemberFetcher(slackClient, executor);
    }

    @Bean
    @Qualifier("leadershipTeamFetcher")
    @ConditionalOnProperty(value = "team.leadership.static.enabled", havingValue = "false", matchIfMissing = true)
    public TeamMemberFetcher leadershipTeamFetcher(SlackClient slackClient, ExecutorService executor) {
        return new GenericSlackMemberFetcher(slackClient, executor);
    }

    @Bean
    @Qualifier("supportTeamFetcher")
    @ConditionalOnProperty("team.support.static.enabled")
    public TeamMemberFetcher staticSupportTeamFetcher() {
        return new GenericStaticMemberFetcher<>(
                staticSupportProps.members(),
                "support",
                StaticSupportTeamProps.StaticSupportMember::email,
                StaticSupportTeamProps.StaticSupportMember::slackId
        );
    }

    @Bean
    @Qualifier("leadershipTeamFetcher")
    @ConditionalOnProperty("team.leadership.static.enabled")
    public TeamMemberFetcher staticLeadershipTeamFetcher() {
        return new GenericStaticMemberFetcher<>(
                staticLeadershipProps.members(),
                "leadership",
                StaticLeadershipTeamProps.StaticSupportMember::email,
                StaticLeadershipTeamProps.StaticSupportMember::slackId
        );
    }

    @Bean
    public SupportTeamService supportTeamService(
            @Qualifier("supportTeamFetcher") TeamMemberFetcher supportTeamFetcher,
            @Qualifier("leadershipTeamFetcher") TeamMemberFetcher leadershipTeamFetcher) {
        return new SupportTeamService(supportTeamProps, leadershipTeamProps, supportTeamFetcher, leadershipTeamFetcher);
    }
}