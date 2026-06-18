package com.coreeng.supportbot.ticket.rest;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisRepository;
import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.escalation.rest.EscalationUIMapper;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamDisplay;
import com.coreeng.supportbot.teams.TeamMemberFetcher;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TicketUIMapperTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private EscalationUIMapper escalationUIMapper;

    @Mock
    private SlackClient slackClient;

    @Mock
    private TeamService teamService;

    @Mock
    private TeamUIMapper teamUIMapper;

    @Mock
    private SupportTeamService supportTeamService;

    @Mock
    private TicketAssignmentProps assignmentProps;

    private TicketUIMapper ticketUIMapper;

    @BeforeEach
    void setUp() {
        ticketUIMapper = new TicketUIMapper(
                analysisRepository,
                escalationUIMapper,
                slackClient,
                teamService,
                teamUIMapper,
                supportTeamService,
                assignmentProps);
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

        // then
        TicketUI result = assertDoesNotThrow(() -> ticketUIMapper.mapToUI(detailedTicket));
        TeamUI team = requireNonNull(result.team());
        assertEquals(TicketTeam.NOT_A_TENANT_CODE, team.label());
        assertEquals(TicketTeam.NOT_A_TENANT_CODE, team.code());
        assertTrue(team.types().isEmpty());
    }

    @Test
    void mapToUIRendersRetiredTeamLabelWhenNotInConfig() {
        Ticket ticket = Ticket.builder()
                .id(new TicketId(1))
                .channelId("C123")
                .queryTs(MessageTs.of("123.456"))
                .createdMessageTs(MessageTs.of("123.457"))
                .status(TicketStatus.opened)
                .team(new TicketTeam.KnownTeam("deleted-team"))
                .impact("production-blocking")
                .tags(ImmutableList.of())
                .lastInteractedAt(Instant.now())
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());

        TeamDisplay retired = new TeamDisplay("deleted-team", "Deleted Team", ImmutableList.of(), false);
        TeamUI retiredUI = new TeamUI("Deleted Team", "deleted-team", ImmutableList.of(), false);
        when(teamService.resolveForDisplay("deleted-team")).thenReturn(retired);
        when(teamUIMapper.mapToUI(retired)).thenReturn(retiredUI);

        TicketUI result = assertDoesNotThrow(() -> ticketUIMapper.mapToUI(detailedTicket));
        TeamUI team = requireNonNull(result.team());
        assertEquals("Deleted Team", team.label());
        assertEquals("deleted-team", team.code());
        assertFalse(team.active());
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

        TeamDisplay teamDisplay = new TeamDisplay("wow", "wow", ImmutableList.of(TeamType.TENANT), true);
        TeamUI teamUI = new TeamUI("wow", "wow", ImmutableList.of(TeamType.TENANT));

        when(teamService.resolveForDisplay("wow")).thenReturn(teamDisplay);
        when(teamUIMapper.mapToUI(teamDisplay)).thenReturn(teamUI);

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertEquals(teamUI, result.team());
    }

    @Test
    void mapToUIReturnsNullAssigneeWhenTicketHasNoAssignee() {
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
                .assignedTo(null)
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.assignedTo());
    }

    @Test
    void mapToUIReturnsNullAssigneeWhenAssigneeIsOrphaned() {
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
                .assignedTo(SlackId.user("U12345"))
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.assignedTo());
    }

    @Test
    void mapToUIReturnsNullAssigneeWhenAssignmentFeatureIsDisabled() {
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
                .assignedTo(SlackId.user("U12345"))
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());
        when(assignmentProps.enabled()).thenReturn(false);

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.assignedTo());
    }

    @Test
    void mapToUIReturnsNullAssigneeWhenMemberNotFound() {
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
                .assignedTo(SlackId.user("U12345"))
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());
        when(assignmentProps.enabled()).thenReturn(true);
        when(supportTeamService.members()).thenReturn(ImmutableList.of());

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.assignedTo());
    }

    @Test
    void mapToUIReturnsAssigneeEmailWhenMemberFound() {
        // given
        String slackUserId = "U12345";
        String memberEmail = "john.doe@example.com";

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
                .assignedTo(SlackId.user(slackUserId))
                .build();

        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of());

        TeamMemberFetcher.TeamMember member1 =
                new TeamMemberFetcher.TeamMember("other@example.com", SlackId.user("U99999"));
        TeamMemberFetcher.TeamMember member2 = new TeamMemberFetcher.TeamMember(memberEmail, SlackId.user(slackUserId));

        when(assignmentProps.enabled()).thenReturn(true);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(member1, member2));

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertEquals(memberEmail, result.assignedTo());
    }

    @Test
    void mapToUIListDoesNotFetchPermalink() {
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

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.query().link());
        verify(slackClient, never()).getPermalink(any());
    }

    @Test
    void mapToUIDetailReturnsNullLinkWhenPermalinkFails() {
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
        when(slackClient.getPermalink(any())).thenThrow(new SlackException(new RuntimeException("rate limited")));

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket, "some query text");

        // then
        assertNull(result.query().link());
    }

    @Test
    void mapToUIIncludesSummaryWhenAvailable() {
        // given
        TicketId id = new TicketId(1);
        DetailedTicket detailedTicket = minimalDetailedTicket(id);
        when(analysisRepository.findSummaryByTicketId(id)).thenReturn("Cache invalidation resolved the incident");

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertEquals("Cache invalidation resolved the incident", result.summary());
    }

    @Test
    void mapToUIKeepsSummaryNullWhenNotAvailable() {
        // given
        TicketId id = new TicketId(1);
        DetailedTicket detailedTicket = minimalDetailedTicket(id);
        when(analysisRepository.findSummaryByTicketId(id)).thenReturn(null);

        // when
        TicketUI result = ticketUIMapper.mapToUI(detailedTicket);

        // then
        assertNull(result.summary());
    }

    @Test
    void mapToUIListBatchFetchesSummaries() {
        // given
        TicketId id1 = new TicketId(1);
        TicketId id2 = new TicketId(2);
        DetailedTicket ticket1 = minimalDetailedTicket(id1);
        DetailedTicket ticket2 = minimalDetailedTicket(id2);
        ImmutableList<DetailedTicket> tickets = ImmutableList.of(ticket1, ticket2);

        when(analysisRepository.findSummariesByTicketIds(ImmutableList.of(id1, id2)))
                .thenReturn(ImmutableMap.of(id1, "Summary for ticket 1"));

        // when
        ImmutableList<TicketUI> results = ticketUIMapper.mapToUIList(tickets);

        // then
        assertEquals(2, results.size());
        assertEquals("Summary for ticket 1", results.get(0).summary());
        assertNull(results.get(1).summary());
    }

    private static DetailedTicket minimalDetailedTicket(TicketId id) {
        Ticket ticket = Ticket.builder()
                .id(id)
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
        return new DetailedTicket(ticket, ImmutableList.of());
    }
}
