package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.teams.PlatformUser;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamMemberFetcher;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private TeamService teamService;

    @Mock
    private SupportTeamService supportTeamService;

    private TeamUIMapper mapper;

    @InjectMocks
    private UsersController controller;

    @BeforeEach
    void setUp() {
        mapper = new TeamUIMapper();
        controller = new UsersController(platformTeamsService, teamService, mapper, supportTeamService);
    }

    @Test
    void shouldReturnUserWhenFound() {
        // given
        String email = "user@test.com";
        PlatformTeam platformTeam = new PlatformTeam("team1", Set.of("group-1"), Set.of());
        PlatformUser user = new PlatformUser(email, Set.of(platformTeam));

        Team supportTeam = new Team("Support Team", "support", ImmutableList.of(TeamType.support));
        Team tenantTeam = new Team("team1", "team1", ImmutableList.of(TeamType.tenant));

        when(platformTeamsService.findUserByEmail(email)).thenReturn(user);
        when(teamService.listTeamsByUserEmail(email)).thenReturn(ImmutableList.of(tenantTeam, supportTeam));

        // when
        ResponseEntity<UserUI> response = controller.findByEmail(email);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        UserUI body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.teams()).hasSize(2);
        assertThat(body.teams().get(0).code()).isEqualTo("team1");
        assertThat(body.teams().get(1).code()).isEqualTo("support");

        verify(platformTeamsService).findUserByEmail(email);
        verify(teamService).listTeamsByUserEmail(email);
        verifyNoMoreInteractions(platformTeamsService, teamService);
    }

    @Test
    void shouldReturnSupportOnlyUserWhenNoPlatformUser() {
        // given
        String email = "support-only@test.com";
        Team supportTeam = new Team("Support Team", "support", ImmutableList.of(TeamType.support));

        when(platformTeamsService.findUserByEmail(email)).thenReturn(null);
        when(teamService.listTeamsByUserEmail(email)).thenReturn(ImmutableList.of(supportTeam));

        // when
        ResponseEntity<UserUI> response = controller.findByEmail(email);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        UserUI body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.teams()).hasSize(1);
        assertThat(body.teams().get(0).code()).isEqualTo("support");

        verify(platformTeamsService).findUserByEmail(email);
        verify(teamService).listTeamsByUserEmail(email);
        verifyNoMoreInteractions(platformTeamsService, teamService);
    }

    @Test
    void shouldReturnNotFoundWhenUserAndTeamsMissing() {
        // given
        String email = "missing@test.com";
        when(platformTeamsService.findUserByEmail(email)).thenReturn(null);
        when(teamService.listTeamsByUserEmail(email)).thenReturn(ImmutableList.of());

        // when
        ResponseEntity<UserUI> response = controller.findByEmail(email);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();

        verify(platformTeamsService).findUserByEmail(email);
        verify(teamService).listTeamsByUserEmail(email);
        verifyNoMoreInteractions(platformTeamsService, teamService);
    }

    @Test
    void shouldReturnEmptyListWhenNoSupportMembers() {
        // given
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        ResponseEntity<List<SupportMemberUI>> response = controller.listSupportMembers();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();

        verify(supportTeamService).members();
        verifyNoMoreInteractions(supportTeamService);
    }

    @Test
    void shouldReturnSupportMembersList() {
        // given
        TeamMemberFetcher.TeamMember member1 = new TeamMemberFetcher.TeamMember(
            "john.doe@example.com",
            SlackId.user("U12345")
        );
        TeamMemberFetcher.TeamMember member2 = new TeamMemberFetcher.TeamMember(
            "jane.smith@example.com",
            SlackId.user("U67890")
        );
        TeamMemberFetcher.TeamMember member3 = new TeamMemberFetcher.TeamMember(
            "bob.jones@example.com",
            SlackId.user("U11111")
        );

        when(supportTeamService.members()).thenReturn(ImmutableList.of(member1, member2, member3));

        // when
        ResponseEntity<List<SupportMemberUI>> response = controller.listSupportMembers();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(3);

        // Verify first member
        List<SupportMemberUI> body = requireNonNull(response.getBody());
        SupportMemberUI ui1 = body.get(0);
        assertThat(ui1.userId()).isEqualTo("U12345");
        assertThat(ui1.displayName()).isEqualTo("john.doe@example.com");

        // Verify second member
        SupportMemberUI ui2 = body.get(1);
        assertThat(ui2.userId()).isEqualTo("U67890");
        assertThat(ui2.displayName()).isEqualTo("jane.smith@example.com");

        // Verify third member
        SupportMemberUI ui3 = body.get(2);
        assertThat(ui3.userId()).isEqualTo("U11111");
        assertThat(ui3.displayName()).isEqualTo("bob.jones@example.com");

        verify(supportTeamService).members();
        verifyNoMoreInteractions(supportTeamService);
    }
}
