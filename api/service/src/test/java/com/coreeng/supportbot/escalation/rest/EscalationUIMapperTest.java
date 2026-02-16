package com.coreeng.supportbot.escalation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EscalationUIMapperTest {

    @Mock
    private TeamService teamService;

    @Mock
    private TeamUIMapper teamUIMapper;

    private EscalationUIMapper escalationUIMapper;

    @BeforeEach
    void setUp() {
        escalationUIMapper = new EscalationUIMapper(teamService, teamUIMapper);
    }

    @Test
    void mapToUIHasThreadWhenThreadTsPresent() {
        // given
        Escalation escalation =
                escalation().threadTs(new MessageTs("1234.5678")).build();

        // when
        EscalationUI result = escalationUIMapper.mapToUI(escalation);

        // then
        assertTrue(result.hasThread());
    }

    @Test
    void mapToUIHasNoThreadWhenThreadTsNull() {
        // given
        Escalation escalation = escalation().threadTs(null).build();

        // when
        EscalationUI result = escalationUIMapper.mapToUI(escalation);

        // then
        assertFalse(result.hasThread());
    }

    @Test
    void mapToUIReturnsTeam() {
        // given
        Escalation escalation = escalation().team("platform").build();
        Team team = new Team("platform", "Platform", ImmutableList.of(TeamType.TENANT));
        TeamUI teamUI = new TeamUI("platform", "Platform", ImmutableList.of(TeamType.TENANT));
        when(teamService.findTeamByCode("platform")).thenReturn(team);
        when(teamUIMapper.mapToUI(team)).thenReturn(teamUI);

        // when
        EscalationUI result = escalationUIMapper.mapToUI(escalation);

        // then
        assertEquals(teamUI, result.team());
    }

    @Test
    void mapToUIReturnsNullTeamWhenNoTeamCode() {
        // given
        Escalation escalation = escalation().team(null).build();

        // when
        EscalationUI result = escalationUIMapper.mapToUI(escalation);

        // then
        assertNull(result.team());
    }

    @Test
    void mapToUIReturnsNullTeamWhenTeamNotFound() {
        // given
        Escalation escalation = escalation().team("deleted-team").build();
        when(teamService.findTeamByCode("deleted-team")).thenReturn(null);

        // when
        EscalationUI result = escalationUIMapper.mapToUI(escalation);

        // then
        assertNull(result.team());
    }

    private Escalation.EscalationBuilder escalation() {
        return Escalation.builder()
                .id(new EscalationId(1))
                .channelId("C123")
                .status(EscalationStatus.opened)
                .ticketId(new TicketId(1))
                .tags(ImmutableList.of())
                .openedAt(Instant.now());
    }
}
