package com.coreeng.supportbot.homepage;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamMemberFetcher;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomepageServiceTest {
    private final String channelId = "U0123";
    private final ImmutableList<String> tags = ImmutableList.of("tag1", "tag2");
    private final MessageTs messageTs = MessageTs.of("123.456");
    private final EscalationStatus escalationStatus = EscalationStatus.opened;

    HomepageService homepageService;
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Mock
    TicketQueryService ticketQueryService;

    @Mock
    SlackClient slackClient;

    @Mock
    ImpactsRegistry impactsRegistry;

    @Mock
    SupportTeamService supportTeamService;

    @Mock
    TicketAssignmentProps assignmentProps;

    @BeforeEach
    void setup() {
        homepageService = new HomepageService(
                ticketQueryService,
                executorService,
                slackClient,
                new SlackTicketsProps(channelId, "eyes", "ticket", "tick", "rocket"),
                impactsRegistry,
                supportTeamService,
                assignmentProps);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void shouldReturnExpectedSingleTicketWithEscalation() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        ImmutableList<Ticket> tickets = buildTickets(1);

        Map<TicketId, Integer> escalationsMap =
                Map.of(requireNonNull(tickets.getFirst().id()), 1);

        ImmutableList<Escalation> escalations = buildEscalationsFromMap(escalationsMap);

        Page<DetailedTicket> ticketPage = new Page<>(buildDetailedTickets(tickets, escalations), 1, 1, 1);

        when(ticketQueryService.findDetailedTicketByQuery(any())).thenReturn(ticketPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);

        ImmutableList<Escalation> actualEscalation =
                requireNonNull(ticketsView.tickets().getFirst().escalations());
        assertThat(actualEscalation.size()).isEqualTo(1);
        assertThat(actualEscalation.getFirst()).usingRecursiveAssertion().isEqualTo(escalations.getFirst());
        assertThat(requireNonNull(ticketsView.tickets().getFirst()).inquiringTeam())
                .isEqualTo("lions");
        assertThat(requireNonNull(ticketsView.tickets().getFirst()).status()).isEqualTo(TicketStatus.opened);
        assertThat(requireNonNull(ticketsView.tickets().getFirst()).impact())
                .isEqualTo(new TicketImpact("Production Blocking", "productionBlocking"));
    }

    @Test
    public void shouldReturnExpectedTicketsSomeWithMultipleEscalations() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        ImmutableList<Ticket> tickets = buildTickets(2);

        Map<TicketId, Integer> escalationsMap = Map.of(
                requireNonNull(tickets.get(0).id()), 2,
                requireNonNull(tickets.get(1).id()), 1);

        ImmutableList<Escalation> escalations = buildEscalationsFromMap(escalationsMap);

        ImmutableList<DetailedTicket> detailedTickets = buildDetailedTickets(tickets, escalations);

        when(ticketQueryService.findDetailedTicketByQuery(any()))
                .thenReturn(new Page<>(detailedTickets, 1, 1, detailedTickets.size()));
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        Map<TicketId, List<Escalation>> expectedEscalationsMap =
                escalations.stream().collect(Collectors.groupingBy(Escalation::ticketId));

        for (TicketView ticket : ticketsView.tickets()) {
            TicketId ticketId = ticket.id();
            List<Escalation> expectedEscalation = expectedEscalationsMap.getOrDefault(ticketId, List.of());
            List<Escalation> actualEscalation = ticket.escalations();

            assertThat(actualEscalation).usingRecursiveAssertion().isEqualTo(expectedEscalation);
        }
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(2);

        assertThat(requireNonNull(ticketsView.tickets().get(0).escalations()).size())
                .isEqualTo(2);
        assertThat(requireNonNull(ticketsView.tickets().get(1).escalations()).size())
                .isEqualTo(1);
    }

    @Test
    public void shouldReturnExpectedTicketsWithOneEscalationEach() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        ImmutableList<Ticket> tickets = buildTickets(3);

        Map<TicketId, Integer> escalationsMap = Map.of(
                requireNonNull(tickets.get(0).id()), 1,
                requireNonNull(tickets.get(1).id()), 1,
                requireNonNull(tickets.get(2).id()), 1);

        ImmutableList<Escalation> escalations = buildEscalationsFromMap(escalationsMap);

        ImmutableList<DetailedTicket> detailedTickets = buildDetailedTickets(tickets, escalations);

        when(ticketQueryService.findDetailedTicketByQuery(any()))
                .thenReturn(new Page<>(detailedTickets, 1, 1, detailedTickets.size()));
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        Map<TicketId, List<Escalation>> expectedEscalationsMap =
                escalations.stream().collect(Collectors.groupingBy(Escalation::ticketId));

        for (TicketView ticket : ticketsView.tickets()) {
            TicketId ticketId = ticket.id();
            List<Escalation> expectedEscalation = expectedEscalationsMap.getOrDefault(ticketId, List.of());
            List<Escalation> actualEscalation = ticket.escalations();

            assertThat(actualEscalation).usingRecursiveAssertion().isEqualTo(expectedEscalation);
        }
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(3);

        assertThat(requireNonNull(ticketsView.tickets().get(0).escalations()).size())
                .isEqualTo(1);
        assertThat(requireNonNull(ticketsView.tickets().get(1).escalations()).size())
                .isEqualTo(1);
        assertThat(requireNonNull(ticketsView.tickets().get(2).escalations()).size())
                .isEqualTo(1);
    }

    @Test
    public void shouldReturnExpectedTicketsWithNoEscalations() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        ImmutableList<Ticket> tickets = buildTickets(2);

        ImmutableList<DetailedTicket> detailedTickets = buildDetailedTickets(tickets, ImmutableList.of());

        when(ticketQueryService.findDetailedTicketByQuery(any()))
                .thenReturn(new Page<>(detailedTickets, 1, 1, detailedTickets.size()));
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));
        // assignmentProps.enabled() is not called when tickets have no assignee (assignedTo is null)

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(2);

        assertThat(requireNonNull(ticketsView.tickets().get(0).escalations()).size())
                .isEqualTo(0);
        assertThat(requireNonNull(ticketsView.tickets().get(1).escalations()).size())
                .isEqualTo(0);
    }

    @Test
    public void shouldReturnAssignedToEmailWhenTicketHasAssignee() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        String assignedToUserId = "U12345";
        String assigneeEmail = "assignee@example.com";
        Ticket ticket = Ticket.builder()
                .channelId(channelId)
                .createdMessageTs(MessageTs.of(messageTs.ts() + 1))
                .tags(tags)
                .id(new TicketId(1))
                .impact("Production Blocking")
                .lastInteractedAt(Instant.now())
                .status(TicketStatus.opened)
                .queryTs(messageTs)
                .team(TicketTeam.fromCode("lions"))
                .assignedTo(SlackId.user(assignedToUserId))
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        TeamMemberFetcher.TeamMember teamMember =
                new TeamMemberFetcher.TeamMember(assigneeEmail, SlackId.user(assignedToUserId));

        Page<DetailedTicket> ticketPage =
                new Page<>(buildDetailedTickets(ImmutableList.of(ticket), ImmutableList.of()), 1, 1, 1);

        when(ticketQueryService.findDetailedTicketByQuery(any())).thenReturn(ticketPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));
        when(assignmentProps.enabled()).thenReturn(true);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(teamMember));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);
        assertThat(ticketsView.tickets().getFirst().assignedTo()).isEqualTo(assigneeEmail);
    }

    @Test
    public void shouldReturnNullAssignedToWhenAssignmentIsDisabled() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        String assignedToUserId = "U12345";
        Ticket ticket = Ticket.builder()
                .channelId(channelId)
                .createdMessageTs(MessageTs.of(messageTs.ts() + 1))
                .tags(tags)
                .id(new TicketId(1))
                .impact("Production Blocking")
                .lastInteractedAt(Instant.now())
                .status(TicketStatus.opened)
                .queryTs(messageTs)
                .team(TicketTeam.fromCode("lions"))
                .assignedTo(SlackId.user(assignedToUserId))
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        Page<DetailedTicket> ticketPage =
                new Page<>(buildDetailedTickets(ImmutableList.of(ticket), ImmutableList.of()), 1, 1, 1);

        when(ticketQueryService.findDetailedTicketByQuery(any())).thenReturn(ticketPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));
        when(assignmentProps.enabled()).thenReturn(false);

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);
        assertThat(ticketsView.tickets().getFirst().assignedTo()).isNull();
    }

    @Test
    public void shouldReturnNullAssignedToWhenAssigneeIsOrphaned() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        String assignedToUserId = "U12345";
        Ticket ticket = Ticket.builder()
                .channelId(channelId)
                .createdMessageTs(MessageTs.of(messageTs.ts() + 1))
                .tags(tags)
                .id(new TicketId(1))
                .impact("Production Blocking")
                .lastInteractedAt(Instant.now())
                .status(TicketStatus.opened)
                .queryTs(messageTs)
                .team(TicketTeam.fromCode("lions"))
                .assignedTo(SlackId.user(assignedToUserId))
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        Page<DetailedTicket> ticketPage =
                new Page<>(buildDetailedTickets(ImmutableList.of(ticket), ImmutableList.of()), 1, 1, 1);

        when(ticketQueryService.findDetailedTicketByQuery(any())).thenReturn(ticketPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));
        // assignmentProps.enabled() is not called when orphaned is true (returns early)

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);
        assertThat(ticketsView.tickets().getFirst().assignedTo()).isNull();
    }

    @Test
    public void shouldReturnNullAssignedToWhenAssigneeNotFoundInSupportTeam() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        String assignedToUserId = "U12345";
        Ticket ticket = Ticket.builder()
                .channelId(channelId)
                .createdMessageTs(MessageTs.of(messageTs.ts() + 1))
                .tags(tags)
                .id(new TicketId(1))
                .impact("Production Blocking")
                .lastInteractedAt(Instant.now())
                .status(TicketStatus.opened)
                .queryTs(messageTs)
                .team(TicketTeam.fromCode("lions"))
                .assignedTo(SlackId.user(assignedToUserId))
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        TeamMemberFetcher.TeamMember otherMember =
                new TeamMemberFetcher.TeamMember("other@example.com", SlackId.user("U99999"));

        Page<DetailedTicket> ticketPage =
                new Page<>(buildDetailedTickets(ImmutableList.of(ticket), ImmutableList.of()), 1, 1, 1);

        when(ticketQueryService.findDetailedTicketByQuery(any())).thenReturn(ticketPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));
        when(assignmentProps.enabled()).thenReturn(true);
        when(supportTeamService.members()).thenReturn(ImmutableList.of(otherMember));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);
        assertThat(ticketsView.tickets().getFirst().assignedTo()).isNull();
    }

    @Test
    public void shouldReturnNullAssignedToWhenTicketHasNoAssignee() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build())
                .build();

        Ticket ticket = Ticket.builder()
                .channelId(channelId)
                .createdMessageTs(MessageTs.of(messageTs.ts() + 1))
                .tags(tags)
                .id(new TicketId(1))
                .impact("Production Blocking")
                .lastInteractedAt(Instant.now())
                .status(TicketStatus.opened)
                .queryTs(messageTs)
                .team(TicketTeam.fromCode("lions"))
                .assignedTo(null)
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();

        Page<DetailedTicket> ticketPage =
                new Page<>(buildDetailedTickets(ImmutableList.of(ticket), ImmutableList.of()), 1, 1, 1);

        when(ticketQueryService.findDetailedTicketByQuery(any())).thenReturn(ticketPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any()))
                .thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));
        // assignmentProps.enabled() is not called when assignedTo is null (returns early)

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);
        assertThat(ticketsView.tickets().getFirst().assignedTo()).isNull();
    }

    private ImmutableList<Ticket> buildTickets(int numberOfTickets) {
        ImmutableList.Builder<Ticket> builder = ImmutableList.builder();
        for (int i = 1; i <= numberOfTickets; i++) {
            builder.add(Ticket.builder()
                    .channelId(channelId)
                    .createdMessageTs(MessageTs.of(messageTs.ts() + i))
                    .tags(tags)
                    .id(new TicketId(i))
                    .impact("Production Blocking")
                    .lastInteractedAt(Instant.now())
                    .status(TicketStatus.opened)
                    .queryTs(messageTs)
                    .team(TicketTeam.fromCode("lions"))
                    .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                    .build());
        }
        return builder.build();
    }

    private ImmutableList<DetailedTicket> buildDetailedTickets(
            ImmutableList<Ticket> tickets, ImmutableList<Escalation> escalations) {
        Map<TicketId, List<Escalation>> escalationsByTicket =
                escalations.stream().collect(Collectors.groupingBy(Escalation::ticketId));

        return ImmutableList.copyOf(tickets.stream()
                .map(t -> new DetailedTicket(
                        t, ImmutableList.copyOf(escalationsByTicket.getOrDefault(t.id(), List.of()))))
                .collect(Collectors.toList()));
    }

    private ImmutableList<Escalation> buildEscalationsFromMap(Map<TicketId, Integer> escalationsPerTicket) {
        ImmutableList.Builder<Escalation> builder = ImmutableList.builder();
        int counter = 1;

        for (Map.Entry<TicketId, Integer> entry : escalationsPerTicket.entrySet()) {
            TicketId ticketId = entry.getKey();
            int escalationCount = entry.getValue();

            for (int i = 0; i < escalationCount; i++) {
                builder.add(Escalation.builder()
                        .channelId(channelId)
                        .tags(tags)
                        .createdMessageTs(MessageTs.of(messageTs.ts() + counter))
                        .status(escalationStatus)
                        .id(new EscalationId(counter))
                        .team("panthers")
                        .ticketId(ticketId)
                        .build());
                counter++;
            }
        }

        return builder.build();
    }
}
