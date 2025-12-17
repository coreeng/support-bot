package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.*;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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

        ticket.stubQueryMessageFetch();

        // then
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        assertThat(ticketResponse.query().text()).isEqualTo(messageText);
    }

    @Test
    public void whenTicketIsUpdated_viaApi_thenFieldsChange() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        Ticket ticket = asTenant.ticket().create(builder -> builder
                .message("Initial query")
        );

        var updateStub = ticket.slackWiremock().stubMessageUpdated(
                MessageUpdatedExpectation.<TicketMessage>builder()
                        .channelId(ticket.channelId())
                        .ts(ticket.formMessageTs())
                        .threadTs(ticket.queryTs())
                        .receiver(new TicketMessage.Receiver())
                        .build()
        );
        var closeReaction = ticket.slackWiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .reaction("white_check_mark")
                        .channelId(ticket.channelId())
                        .ts(ticket.queryTs())
                        .build()
        );

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
        updateStub.assertIsCalled("ticket form message updated");
        closeReaction.assertIsCalled("ticket close reaction added");
    }
}
