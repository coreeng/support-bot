package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SupportTeamServiceTest {

    private SupportTeamProps supportProps;
    private SupportLeadershipTeamProps leadershipProps;
    private TeamMemberFetcher supportMemberFetcher;
    private TeamMemberFetcher leadershipMemberFetcher;

    private SupportTeamService service;

    @BeforeEach
    void setUp() {
        supportProps = new SupportTeamProps("Support Team", "support", "SUPPORT_ID");
        leadershipProps = new SupportLeadershipTeamProps("Leadership Team", "leadership", "LEADERSHIP_ID");
        supportMemberFetcher = mock(TeamMemberFetcher.class);
        leadershipMemberFetcher = mock(TeamMemberFetcher.class);

        service = new SupportTeamService(supportProps, leadershipProps, supportMemberFetcher, leadershipMemberFetcher);
    }

    @Test
    void initLoadsSupportAndLeadershipMembers() {
        ImmutableList<TeamMemberFetcher.TeamMember> supportMembers = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("a@c.com", SlackId.user("U1"))
        );
        ImmutableList<TeamMemberFetcher.TeamMember> leadershipMembers = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("b@c.com", SlackId.user("U2"))
        );
        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(supportMembers);
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(leadershipMembers);

        service.init(); // package-private, ok in same package

        assertThat(service.members()).containsExactlyElementsOf(supportMembers);
        assertThat(service.leadershipMembers()).containsExactlyElementsOf(leadershipMembers);
        verify(supportMemberFetcher).loadInitialMembers(SlackId.group("SUPPORT_ID"));
        verify(leadershipMemberFetcher).loadInitialMembers(SlackId.group("LEADERSHIP_ID"));
    }

    @Test
    void isMemberChecksCaseInsensitive() {
        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(
            ImmutableList.of(new TeamMemberFetcher.TeamMember("USER@c.com", SlackId.user("U1")))
        );
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(ImmutableList.of());

        service.init();

        assertThat(service.isMemberByUserEmail("user@C.com")).isTrue();
    }

    @Test
    void isLeadershipMemberChecksCaseInsensitive() {
        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(ImmutableList.of());
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(
            ImmutableList.of(new TeamMemberFetcher.TeamMember("LEAD@c.com", SlackId.user("U2")))
        );

        service.init();

        assertThat(service.isLeadershipMemberByUserEmail("lead@C.com")).isTrue();
    }

    @Test
    void handleSupportMembershipUpdateUpdatesWhenNonEmpty() {
        ImmutableList<TeamMemberFetcher.TeamMember> updated = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("x@y.com", SlackId.user("UX"))
        );
        when(supportMemberFetcher.handleMembershipUpdate(SlackId.group("SUPPORT_ID"), ImmutableList.of(SlackId.user("u1"))))
            .thenReturn(updated);

        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(ImmutableList.of());
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(ImmutableList.of());
        service.init();

        service.handleMembershipUpdate(SlackId.group("SUPPORT_ID"), ImmutableList.of(SlackId.user("u1")));

        assertThat(service.members()).containsExactlyElementsOf(updated);
    }

    @Test
    void handleSupportMembershipUpdateIgnoresEmptyResults() {
        ImmutableList<TeamMemberFetcher.TeamMember> initial = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("keep@y.com", SlackId.user("UO"))
        );
        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(initial);
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(ImmutableList.of());
        when(supportMemberFetcher.handleMembershipUpdate(SlackId.group("SUPPORT_ID"), ImmutableList.of(SlackId.user("u1"))))
            .thenReturn(ImmutableList.of());

        service.init();
        service.handleMembershipUpdate(SlackId.group("SUPPORT_ID"), ImmutableList.of(SlackId.user("u1")));

        assertThat(service.members()).containsExactlyElementsOf(initial);
    }

    @Test
    void handleLeadershipMembershipUpdateUpdatesWhenNonEmpty() {
        ImmutableList<TeamMemberFetcher.TeamMember> updated = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("lead@y.com", SlackId.user("UL"))
        );
        when(leadershipMemberFetcher.handleMembershipUpdate(SlackId.group("LEADERSHIP_ID"), ImmutableList.of(SlackId.user("u2"))))
            .thenReturn(updated);

        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(ImmutableList.of());
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(ImmutableList.of());
        service.init();

        service.handleMembershipUpdate(SlackId.group("LEADERSHIP_ID"), ImmutableList.of(SlackId.user("u2")));

        assertThat(service.leadershipMembers()).containsExactlyElementsOf(updated);
    }

    @Test
    void handleLeadershipMembershipUpdateIgnoresEmptyResults() {
        ImmutableList<TeamMemberFetcher.TeamMember> initial = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("keep@y.com", SlackId.user("UL"))
        );
        when(supportMemberFetcher.loadInitialMembers(SlackId.group("SUPPORT_ID"))).thenReturn(ImmutableList.of());
        when(leadershipMemberFetcher.loadInitialMembers(SlackId.group("LEADERSHIP_ID"))).thenReturn(initial);
        when(leadershipMemberFetcher.handleMembershipUpdate(SlackId.group("LEADERSHIP_ID"), ImmutableList.of(SlackId.user("u2"))))
            .thenReturn(ImmutableList.of());

        service.init();
        service.handleMembershipUpdate(SlackId.group("LEADERSHIP_ID"), ImmutableList.of(SlackId.user("u2")));

        assertThat(service.leadershipMembers()).containsExactlyElementsOf(initial);
    }
}

