package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.*;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TestKitExtension.class)
public class TicketApiTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @Test
    public void whenTicketIsFetched_queryTextIsReturned() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        String messageText = "Functional test query text";

        // when
        Ticket ticket = asSupport.ticket().create(builder -> builder
                .message(messageText)
        );

        // then
        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        assertThat(ticketResponse.query().text()).isEqualTo(messageText);
    }

    @Test
    public void whenTicketIsUpdated_viaApi_thenFieldsChange() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        Ticket ticket = asTenant.ticket().create(builder -> builder
                .message("Initial query")
        );

        var closeStubs = ticket.stubCloseFlow("ticket closed");

        // when
        var updated = supportBotClient.updateTicket(
                ticket.id(),
                SupportBotClient.UpdateTicketRequest.builder()
                        .status("closed")
                        .authorsTeam("wow")
                        .tags(ImmutableList.of("ingresses", "networking"))
                        .impact("productionBlocking")
                        .build()
        );

        // then
        assertThat(updated.status()).isEqualTo("closed");
        assertThat(updated.team().code()).isEqualTo("wow");
        assertThat(updated.tags()).containsExactlyInAnyOrder("ingresses", "networking");
        assertThat(updated.impact()).isEqualTo("productionBlocking");
        closeStubs.awaitAllCalled(Duration.ofSeconds(1));
    }
}
