package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.FullSummaryButtonClick;
import com.coreeng.supportbot.testkit.FullSummaryForm;
import com.coreeng.supportbot.testkit.FullSummaryFormSubmission;
import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.Stub;
import com.coreeng.supportbot.testkit.StubWithResult;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.ThreadMessagePostedExpectation;
import com.coreeng.supportbot.testkit.Ticket;
import com.coreeng.supportbot.testkit.TicketMessage;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static com.coreeng.supportbot.testkit.UserRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(TestKitExtension.class)
public class TicketManagementTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;
    private SlackWiremock slackWiremock;

    @Test
    public void whenQueryIsPosted_thenQueryIsRegisteredInBot() {
        // given
        SlackTestKit asTenant = testKit.as(tenant).slack();

        // when
        SlackMessage tenantsMessage = asTenant.postMessage(
            MessageTs.now(),
            "Please, help me with my query"
        );

        // then
        supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts());
    }

    @Test
    public void whenSupportReactedWithEyes_ticketRegistered() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        // when
        SlackMessage tenantsMessage = asTenantSlack.postMessage(
            MessageTs.now(),
            "Please, help me with my query"
        );
        Stub ticketReactionAddedStub = tenantsMessage.expectReactionAdded("ticket");
        MessageTs ticketMessageTs = MessageTs.now();
        StubWithResult<TicketMessage> ticketMessagePostedStub = tenantsMessage
            .expectThreadMessagePosted(ThreadMessagePostedExpectation.<TicketMessage>builder()
                .receiver(new TicketMessage.Receiver())
                .from(supportBot)
                .newMessageTs(ticketMessageTs)
                .build());

        asSupportSlack.addReactionTo(tenantsMessage, "eyes");

        // then
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                ticketReactionAddedStub.assertIsCalled();
                ticketMessagePostedStub.assertIsCalled();
            });
        TicketMessage ticketMessage = ticketMessagePostedStub.result();
        assertThat(ticketMessage).isNotNull();
        var ticketResponse = supportBotClient.assertTicketExists(ticketMessage);
        ticketMessage.assertMatches(ticketResponse);
    }

    @Test
    public void whenTicketIsEditedByUsingFullSummaryForm_changesAreSavedToADatabase() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        Ticket ticket = asSupport.ticket().create(t -> t
            .queryTs(MessageTs.now())
            .createdMessageTs(MessageTs.now())
        );

        // when
        String openFullSummaryTriggerId = "whenFullSummaryButtonIsClicked_formIsOpened";
        StubWithResult<FullSummaryForm> fullSummaryFormOpenedExpectation = ticket.expectFullSummaryFormOpened(openFullSummaryTriggerId);
        FullSummaryButtonClick fullSummaryClick = ticket.fullSummaryButtonClick(openFullSummaryTriggerId);
        asSupport.slack().clickMessageButton(fullSummaryClick);
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(fullSummaryFormOpenedExpectation::assertIsCalled);

        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
            .status("opened")
            .team("wow")
            .tags(ImmutableList.of("ingresses", "networking"))
            .impact("productionBlocking")
            .build();
        asSupport.slack().submitView(ticket.fullSummaryFormSubmit(
            openFullSummaryTriggerId,
            values));

        // then
        ticket.applyChangesLocally(values);
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
    }
}
