package com.coreeng.supportbot.ticket.rest;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coreeng.supportbot.escalation.rest.EscalationUIMapper;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.google.common.collect.ImmutableList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamType;
import com.coreeng.supportbot.teams.rest.TeamUI;

@ExtendWith(MockitoExtension.class)
public class TicketUIMapperTest {

    @Mock
    private EscalationUIMapper escalationUIMapper;
    @Mock
    private SlackClient slackClient;
    @Mock
    private TeamService teamService;
    @Mock
    private TeamUIMapper teamUIMapper;

    private TicketUIMapper ticketUIMapper;

    @BeforeEach
    void setUp() {
        ticketUIMapper = new TicketUIMapper(
            escalationUIMapper,
            slackClient,
            teamService,
            teamUIMapper
        );
    }

    @Test
    void mapToUIReturnsNullTeam() {
        // given
        Ticket ticket = Ticket.builder()
            .id(new TicketId(1))
            .channelId("C123")
            .queryTs(MessageTs.of("123.456"))
            .createdMessageTs(MessageTs.of("123.457"))
            .status(TicketStatus.opened)
            .team(null)
            .impact("production-blocking")
            .tags(ImmutableList.of())
            .lastInteractedAt(Instant.now())
            .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
            .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());
        when(slackClient.getPermalink(any())).thenReturn("https://slack.com/permalink");

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.team());
    }

    @Test
    void mapToUIReturnsNotATenantTeam() {
        // given
        Ticket ticket = Ticket.builder()
                .id(new TicketId(1))
                .channelId("C123")
                .queryTs(MessageTs.of("123.456"))
                .createdMessageTs(MessageTs.of("123.457"))
                .status(TicketStatus.opened)
                .team(new TicketTeam.UnknownTeam())
                .impact("production-blocking")
                .tags(ImmutableList.of("ingresses"))
                .lastInteractedAt(Instant.now())
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());

        // when
        when(slackClient.getPermalink(any())).thenReturn("https://slack.com/permalink");

        // then
        TicketUI result = assertDoesNotThrow(() -> ticketUIMapper.mapToUI(detailedTicket));
        assertEquals(TicketTeam.notATenantCode, result.team().label());
        assertEquals(TicketTeam.notATenantCode, result.team().code());
        assertTrue(result.team().types().isEmpty());
    }

    @Test
    void mapToUIReturnsValidTeam() {
        // given
        Ticket ticket = Ticket.builder()
            .id(new TicketId(1))
            .channelId("C123")
            .queryTs(MessageTs.of("123.456"))
            .createdMessageTs(MessageTs.of("123.457"))
            .status(TicketStatus.opened)
            .team(new TicketTeam.KnownTeam("wow"))
            .impact("production-blocking")
            .tags(ImmutableList.of())
            .lastInteractedAt(Instant.now())
            .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
            .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());

        Team team = new Team("wow", "wow", ImmutableList.of(TeamType.tenant));
        TeamUI teamUI = new TeamUI("wow", "wow", ImmutableList.of(TeamType.tenant));

        when(slackClient.getPermalink(any())).thenReturn("https://slack.com/permalink");
        when(teamService.findTeamByCode("wow")).thenReturn(team);
        when(teamUIMapper.mapToUI(team)).thenReturn(teamUI);

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertEquals(teamUI, result.team());
    }
}
