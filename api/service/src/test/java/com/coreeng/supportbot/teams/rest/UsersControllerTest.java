package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.teams.PlatformUser;
import com.coreeng.supportbot.teams.Team;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private TeamService teamService;

    private TeamUIMapper mapper;

    @InjectMocks
    private UsersController controller;

    @BeforeEach
    void setUp() {
        mapper = new TeamUIMapper();
        controller = new UsersController(platformTeamsService, teamService, mapper);
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
    void shouldReturnNotFoundWhenUserMissing() {
        // given
        String email = "missing@test.com";
        when(platformTeamsService.findUserByEmail(email)).thenReturn(null);

        // when
        ResponseEntity<UserUI> response = controller.findByEmail(email);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();

        verify(platformTeamsService).findUserByEmail(email);
        verifyNoInteractions(teamService);
    }
}

