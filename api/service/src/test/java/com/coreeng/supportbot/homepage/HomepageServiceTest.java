package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

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
    EscalationQueryService escalationQueryService;

    @BeforeEach
    void setup() {
        homepageService = new HomepageService(
                ticketQueryService,
                executorService,
                slackClient,
                new SlackTicketsProps(channelId, "eyes", "ticket", "tick", "rocket"),
                impactsRegistry,
                escalationQueryService
        );
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void shouldReturnExpectedSingleTicketWithEscalation() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build()).build();

        ImmutableList<Ticket> tickets = buildTickets(1);

        Map<TicketId, Integer> escalationsMap = Map.of(
            requireNonNull(tickets.getFirst().id()), 1
        );

        ImmutableList<Escalation> escalation = buildEscalationsFromMap(escalationsMap);

        Page<Ticket> ticketPage = new Page<>(ImmutableList.of(tickets.getFirst()), 1, 1, 1);
        Page<Escalation> escalationPage = new Page<>(ImmutableList.of(escalation.getFirst()), 1, 1, 1);

        when(ticketQueryService.findByQuery(any()))
                .thenReturn(ticketPage);
        when(escalationQueryService.findByQuery(any())).thenReturn(escalationPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any())).thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(1);

        ImmutableList<Escalation> actualEscalation = ticketsView.tickets().getFirst().escalations();

        assertThat(actualEscalation).isNotNull();
        assertNotNull(actualEscalation);
        assertThat(actualEscalation.size()).isEqualTo(1);
        assertThat(actualEscalation.getFirst())
                .usingRecursiveAssertion()
                .isEqualTo(escalation.getFirst());
    }

    @Test
    public void shouldReturnExpectedTicketsSomeWithMultipleEscalations() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build()).build();

        ImmutableList<Ticket> tickets = buildTickets(2);

        Map<TicketId, Integer> escalationsMap = Map.of(
            requireNonNull(tickets.get(0).id()), 2,
            requireNonNull(tickets.get(1).id()), 1
        );

        ImmutableList<Escalation> escalations = buildEscalationsFromMap(escalationsMap);

        Page<Ticket> ticketPage = new Page<>(tickets, 1, 1, tickets.size());
        Page<Escalation> escalationPage = new Page<>(escalations, 1, 1, escalations.size());

        when(ticketQueryService.findByQuery(any())).thenReturn(ticketPage);
        when(escalationQueryService.findByQuery(any())).thenReturn(escalationPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any())).thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        Map<TicketId, List<Escalation>> expectedEscalationsMap =
                escalations.stream().collect(Collectors.groupingBy(Escalation::ticketId));

        for (TicketView ticket : ticketsView.tickets()) {
            TicketId ticketId = ticket.id();
            List<Escalation> expectedEscalation = expectedEscalationsMap.getOrDefault(ticketId, List.of());
            List<Escalation> actualEscalation = ticket.escalations();

            assertThat(actualEscalation)
                    .usingRecursiveAssertion()
                    .isEqualTo(expectedEscalation);
        }
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(2);

        assertThat(requireNonNull(ticketsView.tickets().get(0).escalations()).size()).isEqualTo(2);
        assertThat(requireNonNull(ticketsView.tickets().get(1).escalations()).size()).isEqualTo(1);
    }

    @Test
    public void shouldReturnExpectedTicketsWithOneEscalationEach() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build()).build();

        ImmutableList<Ticket> tickets = buildTickets(3);

        Map<TicketId, Integer> escalationsMap = Map.of(
            requireNonNull(tickets.get(0).id()), 1,
            requireNonNull(tickets.get(1).id()), 1,
            requireNonNull(tickets.get(2).id()), 1
        );

        ImmutableList<Escalation> escalations = buildEscalationsFromMap(escalationsMap);

        Page<Ticket> ticketPage = new Page<>(tickets, 1, 1, tickets.size());
        Page<Escalation> escalationPage = new Page<>(escalations, 1, 1, escalations.size());

        when(ticketQueryService.findByQuery(any())).thenReturn(ticketPage);
        when(escalationQueryService.findByQuery(any())).thenReturn(escalationPage);
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any())).thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        Map<TicketId, List<Escalation>> expectedEscalationsMap =
                escalations.stream().collect(Collectors.groupingBy(Escalation::ticketId));

        for (TicketView ticket : ticketsView.tickets()) {
            TicketId ticketId = ticket.id();
            List<Escalation> expectedEscalation = expectedEscalationsMap.getOrDefault(ticketId, List.of());
            List<Escalation> actualEscalation = ticket.escalations();

            assertThat(actualEscalation)
                    .usingRecursiveAssertion()
                    .isEqualTo(expectedEscalation);
        }
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(3);

        assertThat(requireNonNull(ticketsView.tickets().get(0).escalations()).size()).isEqualTo(1);
        assertThat(requireNonNull(ticketsView.tickets().get(1).escalations()).size()).isEqualTo(1);
        assertThat(requireNonNull(ticketsView.tickets().get(2).escalations()).size()).isEqualTo(1);
    }


    @Test
    public void shouldReturnExpectedTicketsWithNoEscalations() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build()).build();

        ImmutableList<Ticket> tickets = buildTickets(2);

        Page<Ticket> ticketPage = new Page<>(tickets, 1, 1, tickets.size());

        when(ticketQueryService.findByQuery(any())).thenReturn(ticketPage);
        when(escalationQueryService.findByQuery(any())).thenReturn(new Page<>(ImmutableList.of(), 1, 1, 0));
        when(slackClient.getPermalink(any())).thenReturn("perma.link");
        when(impactsRegistry.findImpactByCode(any())).thenReturn(new TicketImpact("Production Blocking", "productionBlocking"));

        // when
        HomepageView ticketsView = homepageService.getTicketsView(state);

        // then
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(2);

        assertThat(requireNonNull(ticketsView.tickets().get(0).escalations()).size()).isEqualTo(0);
        assertThat(requireNonNull(ticketsView.tickets().get(1).escalations()).size()).isEqualTo(0);
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
                    .team("lions")
                    .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                    .build());
        }
        return builder.build();
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