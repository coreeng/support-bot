package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SupportTeamServiceTest {

    private SupportTeamProps supportProps;
    private SupportLeadershipTeamProps leadershipProps;
    private SupportMemberFetcher memberFetcher;

    private SupportTeamService service;

    @BeforeEach
    void setUp() {
        supportProps = new SupportTeamProps("Support Team", "support", "SUPPORT_ID");
        leadershipProps = new SupportLeadershipTeamProps("Leadership Team", "leadership", "LEADERSHIP_ID");
        memberFetcher = mock(SupportMemberFetcher.class);

        service = new SupportTeamService(supportProps, leadershipProps, memberFetcher);
    }

    @Test
    void initLoadsSupportAndLeadershipMembers() {
        ImmutableList<SupportMemberFetcher.SupportMember> supportMembers = ImmutableList.of(
            new SupportMemberFetcher.SupportMember("a@c.com", "U1")
        );
        ImmutableList<SupportMemberFetcher.SupportMember> leadershipMembers = ImmutableList.of(
            new SupportMemberFetcher.SupportMember("b@c.com", "U2")
        );
        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(supportMembers);
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(leadershipMembers);

        service.init(); // package-private, ok in same package

        assertThat(service.members()).containsExactlyElementsOf(supportMembers);
        assertThat(service.leadershipMembers()).containsExactlyElementsOf(leadershipMembers);
        verify(memberFetcher).loadInitialSupportMembers("SUPPORT_ID");
        verify(memberFetcher).loadInitialLeadershipMembers("LEADERSHIP_ID");
    }

    @Test
    void isMemberChecksCaseInsensitive() {
        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(
            ImmutableList.of(new SupportMemberFetcher.SupportMember("USER@c.com", "U1"))
        );
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(ImmutableList.of());

        service.init();

        assertThat(service.isMemberByUserEmail("user@C.com")).isTrue();
    }

    @Test
    void isLeadershipMemberChecksCaseInsensitive() {
        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(ImmutableList.of());
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(
            ImmutableList.of(new SupportMemberFetcher.SupportMember("LEAD@c.com", "U2"))
        );

        service.init();

        assertThat(service.isLeadershipMemberByUserEmail("lead@C.com")).isTrue();
    }

    @Test
    void handleSupportMembershipUpdateUpdatesWhenNonEmpty() {
        ImmutableList<SupportMemberFetcher.SupportMember> updated = ImmutableList.of(
            new SupportMemberFetcher.SupportMember("x@y.com", "UX")
        );
        when(memberFetcher.handleSupportMembershipUpdate("SUPPORT_ID", ImmutableList.of("u1")))
            .thenReturn(updated);

        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(ImmutableList.of());
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(ImmutableList.of());
        service.init();

        service.handleMembershipUpdate("SUPPORT_ID", ImmutableList.of("u1"));

        assertThat(service.members()).containsExactlyElementsOf(updated);
    }

    @Test
    void handleSupportMembershipUpdateIgnoresEmptyResults() {
        ImmutableList<SupportMemberFetcher.SupportMember> initial = ImmutableList.of(
            new SupportMemberFetcher.SupportMember("keep@y.com", "UO")
        );
        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(initial);
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(ImmutableList.of());
        when(memberFetcher.handleSupportMembershipUpdate("SUPPORT_ID", ImmutableList.of("u1")))
            .thenReturn(ImmutableList.of());

        service.init();
        service.handleMembershipUpdate("SUPPORT_ID", ImmutableList.of("u1"));

        assertThat(service.members()).containsExactlyElementsOf(initial);
    }

    @Test
    void handleLeadershipMembershipUpdateUpdatesWhenNonEmpty() {
        ImmutableList<SupportMemberFetcher.SupportMember> updated = ImmutableList.of(
            new SupportMemberFetcher.SupportMember("lead@y.com", "UL")
        );
        when(memberFetcher.handleLeadershipMembershipUpdate("LEADERSHIP_ID", ImmutableList.of("u2")))
            .thenReturn(updated);

        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(ImmutableList.of());
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(ImmutableList.of());
        service.init();

        service.handleMembershipUpdate("LEADERSHIP_ID", ImmutableList.of("u2"));

        assertThat(service.leadershipMembers()).containsExactlyElementsOf(updated);
    }

    @Test
    void handleLeadershipMembershipUpdateIgnoresEmptyResults() {
        ImmutableList<SupportMemberFetcher.SupportMember> initial = ImmutableList.of(
            new SupportMemberFetcher.SupportMember("keep@y.com", "UL")
        );
        when(memberFetcher.loadInitialSupportMembers("SUPPORT_ID")).thenReturn(ImmutableList.of());
        when(memberFetcher.loadInitialLeadershipMembers("LEADERSHIP_ID")).thenReturn(initial);
        when(memberFetcher.handleLeadershipMembershipUpdate("LEADERSHIP_ID", ImmutableList.of("u2")))
            .thenReturn(ImmutableList.of());

        service.init();
        service.handleMembershipUpdate("LEADERSHIP_ID", ImmutableList.of("u2"));

        assertThat(service.leadershipMembers()).containsExactlyElementsOf(initial);
    }
}

