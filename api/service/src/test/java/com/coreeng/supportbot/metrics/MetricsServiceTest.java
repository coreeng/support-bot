package com.coreeng.supportbot.metrics;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private TicketQueryService queryService;

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(queryService, meterRegistry);
    }

    @Test
    void registersMetricWithCorrectTags() {
        Ticket ticket = Ticket.builder()
            .id(new TicketId(1))
            .channelId("C123")
            .queryTs(MessageTs.of("123.456"))
            .status(TicketStatus.opened)
            .impact("productionBlocking")
            .team("corePlatform")
            .ratingSubmitted(false)
            .lastInteractedAt(Instant.now())
            .build();
        Escalation escalation = Escalation.builder().id(new EscalationId(1L)).status(EscalationStatus.opened).build();
        DetailedTicket detailedTicket = new DetailedTicket(ticket, ImmutableList.of(escalation));

        when(queryService.findDetailedTicketByQuery(any()))
            .thenReturn(new Page<>(ImmutableList.of(detailedTicket), 1, 1, 1));

        metricsService.refreshMetrics();

        Gauge metric = meterRegistry.find("supportbot_tickets")
            .tag("ticketId", "1")
            .tag("status", "opened")
            .tag("impact", "productionBlocking")
            .tag("team", "corePlatform")
            .tag("escalated", "true")
            .tag("rated", "false")
            .gauge();

        assertThat(metric).isNotNull();
        assertThat(metric.value()).isEqualTo(1.0);
    }
}
