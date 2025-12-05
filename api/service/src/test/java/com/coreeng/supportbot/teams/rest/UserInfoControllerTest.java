package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoControllerTest {

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private SupportLeadershipTeamProps leadershipTeamProps;

    @Mock
    private SupportTeamService supportTeamService;

    private UserInfoController controller;

    @BeforeEach
    void setUp() {
        controller = new UserInfoController(
                platformTeamsService,
                leadershipTeamProps,
                supportTeamService
        );
    }

    @Test
    void shouldReturnUserInfoWhenUserExists() {
        // given
        String email = "user@example.com";
        PlatformTeam team1 = new PlatformTeam("team1", ImmutableSet.of("group-ref-1"), ImmutableSet.of());
        PlatformTeam team2 = new PlatformTeam("team2", ImmutableSet.of("group-ref-2", "group-ref-3"), ImmutableSet.of());
        PlatformUser user = new PlatformUser(email, ImmutableSet.of(team1, team2));

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.teams()).hasSize(2);
        assertThat(body.isLeadership()).isFalse();
        assertThat(body.isSupportEngineer()).isFalse();

        TeamDto teamDto1 = body.teams().get(0);
        assertThat(teamDto1.name()).isEqualTo("team1");
        assertThat(teamDto1.groupRefs()).containsExactly("group-ref-1");

        TeamDto teamDto2 = body.teams().get(1);
        assertThat(teamDto2.name()).isEqualTo("team2");
        assertThat(teamDto2.groupRefs()).containsExactlyInAnyOrder("group-ref-2", "group-ref-3");

        verify(platformTeamsService).findUserByEmail(email);
        verify(leadershipTeamProps).enabled();
        verify(supportTeamService).members();
        verifyNoMoreInteractions(platformTeamsService, leadershipTeamProps, supportTeamService);
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() {
        // given
        String email = "nonexistent@example.com";
        when(platformTeamsService.findUserByEmail(email)).thenReturn(null);

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(404);
        assertThat(response.getBody()).isNull();

        verify(platformTeamsService).findUserByEmail(email);
        verifyNoInteractions(leadershipTeamProps, supportTeamService);
    }

    @Test
    void shouldMarkUserAsLeadershipWhenEmailMatches() {
        // given
        String email = "leader@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        ImmutableList<String> leadershipEmails = ImmutableList.of("leader@example.com", "other@example.com");

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(true);
        when(leadershipTeamProps.memberEmails()).thenReturn(leadershipEmails);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isTrue();
        assertThat(body.isSupportEngineer()).isFalse();

        verify(platformTeamsService).findUserByEmail(email);
        verify(leadershipTeamProps).enabled();
        verify(leadershipTeamProps, times(2)).memberEmails(); // Changed: expects 2 calls
        verify(supportTeamService).members();
        verifyNoMoreInteractions(platformTeamsService, leadershipTeamProps, supportTeamService);
    }

    @Test
    void shouldMarkUserAsLeadershipWhenEmailMatchesCaseInsensitive() {
        // given
        String email = "LEADER@EXAMPLE.COM";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        ImmutableList<String> leadershipEmails = ImmutableList.of("leader@example.com");

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(true);
        when(leadershipTeamProps.memberEmails()).thenReturn(leadershipEmails);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isTrue();

        verify(leadershipTeamProps, times(2)).memberEmails(); // Add this verification
    }

    @Test
    void shouldNotMarkUserAsLeadershipWhenLeadershipDisabled() {
        // given
        String email = "leader@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isFalse();

        verify(leadershipTeamProps).enabled();
        verify(leadershipTeamProps, never()).memberEmails();
    }

    @Test
    void shouldNotMarkUserAsLeadershipWhenEmailDoesNotMatch() {
        // given
        String email = "user@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        ImmutableList<String> leadershipEmails = ImmutableList.of("leader@example.com", "other@example.com");

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(true);
        when(leadershipTeamProps.memberEmails()).thenReturn(leadershipEmails);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isFalse();

        verify(leadershipTeamProps, times(2)).memberEmails(); // Add this verification
    }

    @Test
    void shouldHandleNullLeadershipEmails() {
        // given
        String email = "user@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(true);
        when(leadershipTeamProps.memberEmails()).thenReturn(null);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isFalse();
    }

    @Test
    void shouldMarkUserAsSupportEngineerWhenEmailMatches() {
        // given
        String email = "support@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        SupportMemberFetcher.SupportMember supportMember = new SupportMemberFetcher.SupportMember(
                "support@example.com",
                "U123456"
        );

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(supportMember));

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isFalse();
        assertThat(body.isSupportEngineer()).isTrue();
    }

    @Test
    void shouldMarkUserAsSupportEngineerWhenEmailMatchesCaseInsensitive() {
        // given
        String email = "SUPPORT@EXAMPLE.COM";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        SupportMemberFetcher.SupportMember supportMember = new SupportMemberFetcher.SupportMember(
                "support@example.com",
                "U123456"
        );

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(supportMember));

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSupportEngineer()).isTrue();
    }

    @Test
    void shouldNotMarkUserAsSupportEngineerWhenEmailDoesNotMatch() {
        // given
        String email = "user@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        SupportMemberFetcher.SupportMember supportMember = new SupportMemberFetcher.SupportMember(
                "support@example.com",
                "U123456"
        );

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(supportMember));

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSupportEngineer()).isFalse();
    }

    @Test
    void shouldMarkUserAsBothLeadershipAndSupport() {
        // given
        String email = "both@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        ImmutableList<String> leadershipEmails = ImmutableList.of("both@example.com");
        SupportMemberFetcher.SupportMember supportMember = new SupportMemberFetcher.SupportMember(
                "both@example.com",
                "U123456"
        );

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(true);
        when(leadershipTeamProps.memberEmails()).thenReturn(leadershipEmails);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(supportMember));

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isLeadership()).isTrue();
        assertThat(body.isSupportEngineer()).isTrue();

        verify(leadershipTeamProps, times(2)).memberEmails(); // Add this verification
    }

    @Test
    void shouldHandleUserWithNoTeams() {
        // given
        String email = "user@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.teams()).isEmpty();
        assertThat(body.isLeadership()).isFalse();
        assertThat(body.isSupportEngineer()).isFalse();
    }

    @Test
    void shouldHandleUserWithMultipleTeams() {
        // given
        String email = "user@example.com";
        PlatformTeam team1 = new PlatformTeam("team1", ImmutableSet.of("ref1"), ImmutableSet.of());
        PlatformTeam team2 = new PlatformTeam("team2", ImmutableSet.of("ref2"), ImmutableSet.of());
        PlatformTeam team3 = new PlatformTeam("team3", ImmutableSet.of("ref3", "ref4"), ImmutableSet.of());
        PlatformUser user = new PlatformUser(email, ImmutableSet.of(team1, team2, team3));

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.teams()).hasSize(3);
        assertThat(body.teams().stream().map(TeamDto::name))
                .containsExactlyInAnyOrder("team1", "team2", "team3");
    }

    @Test
    void shouldHandleMultipleSupportMembers() {
        // given
        String email = "support@example.com";
        PlatformUser user = new PlatformUser(email, ImmutableSet.of());
        SupportMemberFetcher.SupportMember member1 = new SupportMemberFetcher.SupportMember(
                "other@example.com",
                "U111"
        );
        SupportMemberFetcher.SupportMember member2 = new SupportMemberFetcher.SupportMember(
                "support@example.com",
                "U222"
        );
        SupportMemberFetcher.SupportMember member3 = new SupportMemberFetcher.SupportMember(
                "another@example.com",
                "U333"
        );

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(member1, member2, member3));

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSupportEngineer()).isTrue();
    }

    @Test
    void shouldHandleTeamWithEmptyGroupRefs() {
        // given
        String email = "user@example.com";
        PlatformTeam team = new PlatformTeam("team1", ImmutableSet.of(), ImmutableSet.of());
        PlatformUser user = new PlatformUser(email, ImmutableSet.of(team));

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(leadershipTeamProps.enabled()).thenReturn(false);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        var response = controller.getUserInfo(email);

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        UserInfoResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.teams()).hasSize(1);
        TeamDto teamDto = body.teams().get(0);
        assertThat(teamDto.name()).isEqualTo("team1");
        assertThat(teamDto.groupRefs()).isEmpty();
    }
}
