package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.client.SlackClient;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportTeamConfigTest {

    private SupportTeamConfig config;
    private SlackClient slackClient;
    private ExecutorService executor;
    private SupportTeamProps supportTeamProps;
    private SupportLeadershipTeamProps leadershipTeamProps;
    private StaticSupportTeamProps staticSupportProps;
    private StaticLeadershipTeamProps staticLeadershipProps;

    @BeforeEach
    void setUp() {
        slackClient = mock(SlackClient.class);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        supportTeamProps = new SupportTeamProps("Support", "support", "SUPPORT_GROUP");
        leadershipTeamProps = new SupportLeadershipTeamProps("Leadership", "leadership", "LEADERSHIP_GROUP");
        staticSupportProps = new StaticSupportTeamProps(
                true, List.of(new StaticSupportTeamProps.StaticSupportMember("support@example.com", "S1")));
        staticLeadershipProps = new StaticLeadershipTeamProps(
                true, List.of(new StaticLeadershipTeamProps.StaticSupportMember("leader@example.com", "L1")));

        config =
                new SupportTeamConfig(supportTeamProps, leadershipTeamProps, staticSupportProps, staticLeadershipProps);
    }

    @Test
    void supportTeamFetcherCreatesGenericSlackMemberFetcher() {
        // When
        TeamMemberFetcher fetcher = config.supportTeamFetcher(slackClient, executor);

        // Then
        assertThat(fetcher).isInstanceOf(GenericSlackMemberFetcher.class);
    }

    @Test
    void leadershipTeamFetcherCreatesGenericSlackMemberFetcher() {
        // When
        TeamMemberFetcher fetcher = config.leadershipTeamFetcher(slackClient, executor);

        // Then
        assertThat(fetcher).isInstanceOf(GenericSlackMemberFetcher.class);
    }

    @Test
    void slackFetchersAreIndependent() {
        // When
        TeamMemberFetcher supportFetcher = config.supportTeamFetcher(slackClient, executor);
        TeamMemberFetcher leadershipFetcher = config.leadershipTeamFetcher(slackClient, executor);

        // Then
        assertThat(supportFetcher).isNotSameAs(leadershipFetcher);
    }

    @Test
    void staticSupportTeamFetcherCreatesGenericStaticMemberFetcher() {
        // When
        TeamMemberFetcher fetcher = config.staticSupportTeamFetcher();

        // Then
        assertThat(fetcher).isInstanceOf(GenericStaticMemberFetcher.class);
    }

    @Test
    void staticLeadershipTeamFetcherCreatesGenericStaticMemberFetcher() {
        // When
        TeamMemberFetcher fetcher = config.staticLeadershipTeamFetcher();

        // Then
        assertThat(fetcher).isInstanceOf(GenericStaticMemberFetcher.class);
    }

    @Test
    void staticFetchersAreIndependent() {
        // When
        TeamMemberFetcher supportFetcher = config.staticSupportTeamFetcher();
        TeamMemberFetcher leadershipFetcher = config.staticLeadershipTeamFetcher();

        // Then
        assertThat(supportFetcher).isNotSameAs(leadershipFetcher);
    }

    @Test
    void supportTeamServiceCanBeCreatedWithBothFetchers() {
        // Given
        TeamMemberFetcher supportFetcher = config.supportTeamFetcher(slackClient, executor);
        TeamMemberFetcher leadershipFetcher = config.leadershipTeamFetcher(slackClient, executor);

        // When
        SupportTeamService service = config.supportTeamService(supportFetcher, leadershipFetcher);

        // Then
        assertThat(service).isNotNull();
    }

    @Test
    void supportTeamServiceCanBeCreatedWithMixedFetchersStaticSupport() {
        // Given
        TeamMemberFetcher supportFetcher = config.staticSupportTeamFetcher();
        TeamMemberFetcher leadershipFetcher = config.leadershipTeamFetcher(slackClient, executor);

        // When
        SupportTeamService service = config.supportTeamService(supportFetcher, leadershipFetcher);

        // Then
        assertThat(service).isNotNull();
    }

    @Test
    void supportTeamServiceCanBeCreatedWithMixedFetchersStaticLeadership() {
        // Given
        TeamMemberFetcher supportFetcher = config.supportTeamFetcher(slackClient, executor);
        TeamMemberFetcher leadershipFetcher = config.staticLeadershipTeamFetcher();

        // When
        SupportTeamService service = config.supportTeamService(supportFetcher, leadershipFetcher);

        // Then
        assertThat(service).isNotNull();
    }

    @Test
    void supportTeamServiceCanBeCreatedWithBothStatic() {
        // Given
        TeamMemberFetcher supportFetcher = config.staticSupportTeamFetcher();
        TeamMemberFetcher leadershipFetcher = config.staticLeadershipTeamFetcher();

        // When
        SupportTeamService service = config.supportTeamService(supportFetcher, leadershipFetcher);

        // Then
        assertThat(service).isNotNull();
    }
}
