package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenericStaticMemberFetcherTest {

    record TestMember(String email, String slackId) {}

    @Test
    void loadInitialMembersReturnsEmptyListWhenNullMembers() {
        // Given
        GenericStaticMemberFetcher<TestMember> fetcher = new GenericStaticMemberFetcher<>(
                null,
                "test-team",
                TestMember::email,
                TestMember::slackId
        );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("GROUP_ID");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void loadInitialMembersReturnsConfiguredMembers() {
        // Given
        List<TestMember> staticMembers = List.of(
                new TestMember("alice@example.com", "U1"),
                new TestMember("bob@example.com", "U2")
        );
        GenericStaticMemberFetcher<TestMember> fetcher = new GenericStaticMemberFetcher<>(
                staticMembers,
                "test-team",
                TestMember::email,
                TestMember::slackId
        );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("GROUP_ID");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactly("alice@example.com", "bob@example.com");
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::slackId)
                .containsExactly("U1", "U2");
    }

    @Test
    void loadInitialMembersIgnoresGroupId() {
        // Given
        List<TestMember> staticMembers = List.of(new TestMember("test@example.com", "U1"));
        GenericStaticMemberFetcher<TestMember> fetcher = new GenericStaticMemberFetcher<>(
                staticMembers,
                "test-team",
                TestMember::email,
                TestMember::slackId
        );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result1 = fetcher.loadInitialMembers("GROUP_A");
        ImmutableList<TeamMemberFetcher.TeamMember> result2 = fetcher.loadInitialMembers("GROUP_B");

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).hasSize(1);
    }

    @Test
    void handleMembershipUpdateReturnsEmptyList() {
        // Given
        List<TestMember> staticMembers = List.of(new TestMember("static@example.com", "U1"));
        GenericStaticMemberFetcher<TestMember> fetcher = new GenericStaticMemberFetcher<>(
                staticMembers,
                "test-team",
                TestMember::email,
                TestMember::slackId
        );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = 
                fetcher.handleMembershipUpdate("GROUP_ID", ImmutableList.of("U100", "U200"));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void handleMembershipUpdateIgnoresTeamUsers() {
        // Given
        List<TestMember> staticMembers = List.of(new TestMember("static@example.com", "U1"));
        GenericStaticMemberFetcher<TestMember> fetcher = new GenericStaticMemberFetcher<>(
                staticMembers,
                "test-team",
                TestMember::email,
                TestMember::slackId
        );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = 
                fetcher.handleMembershipUpdate("GROUP_ID", ImmutableList.of("NEW_USER"));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void worksWithStaticSupportTeamPropsMembers() {
        // Given
        List<StaticSupportTeamProps.StaticSupportMember> staticMembers = List.of(
                new StaticSupportTeamProps.StaticSupportMember("support1@example.com", "S1"),
                new StaticSupportTeamProps.StaticSupportMember("support2@example.com", "S2")
        );
        GenericStaticMemberFetcher<StaticSupportTeamProps.StaticSupportMember> fetcher = 
                new GenericStaticMemberFetcher<>(
                        staticMembers,
                        "support",
                        StaticSupportTeamProps.StaticSupportMember::email,
                        StaticSupportTeamProps.StaticSupportMember::slackId
                );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("SUPPORT_GROUP");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactly("support1@example.com", "support2@example.com");
    }

    @Test
    void worksWithStaticLeadershipTeamPropsMembers() {
        // Given
        List<StaticLeadershipTeamProps.StaticSupportMember> staticMembers = List.of(
                new StaticLeadershipTeamProps.StaticSupportMember("leader1@example.com", "L1"),
                new StaticLeadershipTeamProps.StaticSupportMember("leader2@example.com", "L2")
        );
        GenericStaticMemberFetcher<StaticLeadershipTeamProps.StaticSupportMember> fetcher = 
                new GenericStaticMemberFetcher<>(
                        staticMembers,
                        "leadership",
                        StaticLeadershipTeamProps.StaticSupportMember::email,
                        StaticLeadershipTeamProps.StaticSupportMember::slackId
                );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("LEADERSHIP_GROUP");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactly("leader1@example.com", "leader2@example.com");
    }

    @Test
    void handleEmptyStaticMembersList() {
        // Given
        List<TestMember> staticMembers = List.of();
        GenericStaticMemberFetcher<TestMember> fetcher = new GenericStaticMemberFetcher<>(
                staticMembers,
                "test-team",
                TestMember::email,
                TestMember::slackId
        );

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("GROUP_ID");

        // Then
        assertThat(result).isEmpty();
    }
}

