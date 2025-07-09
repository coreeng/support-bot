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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomepageServiceTest {
    private final String CHANNEL_ID = "U0123";
    private final ImmutableList<String> TAGS = ImmutableList.of("tag1", "tag2");
    private final MessageTs MESSAGE_TS = MessageTs.of("123.456");
    private final EscalationStatus ESCALATION_STATUS = EscalationStatus.opened;

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
                new SlackTicketsProps(CHANNEL_ID, "eyes", "ticket", "tick", "rocket"),
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
                tickets.get(0).id(), 1
        );

        ImmutableList<Escalation> escalation = buildEscalationsFromMap(escalationsMap);

        Page<Ticket> ticketPage = new Page<>(ImmutableList.of(tickets.get(0)), 1, 1, 1);
        Page<Escalation> escalationPage = new Page<>(ImmutableList.of(escalation.get(0)), 1, 1, 1);

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

        ImmutableList<Escalation> actualEscalation = ticketsView.tickets().get(0).escalation();

        assertThat(actualEscalation).isNotNull();
        assertThat(actualEscalation.size()).isEqualTo(1);
        assertThat(actualEscalation.get(0))
                .usingRecursiveAssertion()
                .isEqualTo(escalation.get(0));
    }

    @Test
    public void shouldReturnExpectedTicketsWithMultipleEscalations() {
        // given
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder().build()).build();

        ImmutableList<Ticket> tickets = buildTickets(2);

        Map<TicketId, Integer> escalationsMap = Map.of(
                tickets.get(0).id(), 2,
                tickets.get(1).id(), 1
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
        Map<TicketId, List<Escalation>> expectedEscalationsByTicketId =
                escalations.stream().collect(Collectors.groupingBy(Escalation::ticketId));

        for (TicketView ticket : ticketsView.tickets()) {
            TicketId ticketId = ticket.id();
            List<Escalation> expectedEscalation = expectedEscalationsByTicketId.getOrDefault(ticketId, List.of());
            List<Escalation> actualEscalation = ticket.escalation();

            assertThat(actualEscalation)
                    .usingRecursiveAssertion()
                    .isEqualTo(expectedEscalation);
        }
        assertThat(ticketsView).isNotNull();
        assertThat(ticketsView.tickets().size()).isEqualTo(2);

        assertThat(ticketsView.tickets().get(0).escalation().size()).isEqualTo(2);
        assertThat(ticketsView.tickets().get(1).escalation().size()).isEqualTo(1);
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

        assertThat(ticketsView.tickets().get(0).escalation().size()).isEqualTo(0);
        assertThat(ticketsView.tickets().get(1).escalation().size()).isEqualTo(0);
    }

    private ImmutableList<Ticket> buildTickets(int numberOfTickets) {
        ImmutableList.Builder<Ticket> builder = ImmutableList.builder();
        for (int i = 1; i <= numberOfTickets; i++) {
            builder.add(Ticket.builder()
                    .channelId(CHANNEL_ID)
                    .createdMessageTs(MessageTs.of(MESSAGE_TS.ts() + i))
                    .tags(TAGS)
                    .id(new TicketId(i))
                    .impact("Production Blocking")
                    .lastInteractedAt(Instant.now())
                    .status(TicketStatus.opened)
                    .queryTs(MESSAGE_TS)
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
                        .channelId(CHANNEL_ID)
                        .tags(TAGS)
                        .createdMessageTs(MessageTs.of(MESSAGE_TS.ts() + counter))
                        .status(ESCALATION_STATUS)
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