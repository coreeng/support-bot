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

    @Test
    public void whenTicketsAreBulkReassigned_assigneeIsUpdatedForOpenTickets() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        
        // Create 3 open tickets
        Ticket ticket1 = asTenant.ticket().create(builder -> builder.message("Query 1"));
        Ticket ticket2 = asTenant.ticket().create(builder -> builder.message("Query 2"));
        Ticket ticket3 = asTenant.ticket().create(builder -> builder.message("Query 3"));
        
        String newAssignee = "U0123456782"; // Sana Chernobog
        
        // when
        var result = supportBotClient.bulkReassign(
            SupportBotClient.BulkReassignRequest.builder()
                .ticketIds(ImmutableList.of(ticket1.id(), ticket2.id(), ticket3.id()))
                .assignedTo(newAssignee)
                .build()
        );

        // then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.successfulTicketIds()).containsExactlyInAnyOrder(
            ticket1.id(), ticket2.id(), ticket3.id()
        );
        
        // Verify tickets are reassigned
        ticket1.stubQueryMessageFetch();
        ticket2.stubQueryMessageFetch();
        ticket3.stubQueryMessageFetch();
        
        assertThat(supportBotClient.assertTicketExists(ticket1).assignedTo())
            .isEqualTo("sana.chernobog@company.com");
        assertThat(supportBotClient.assertTicketExists(ticket2).assignedTo())
            .isEqualTo("sana.chernobog@company.com");
        assertThat(supportBotClient.assertTicketExists(ticket3).assignedTo())
            .isEqualTo("sana.chernobog@company.com");
    }
}
