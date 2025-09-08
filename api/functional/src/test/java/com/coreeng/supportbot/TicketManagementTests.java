package com.coreeng.supportbot;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coreeng.supportbot.testkit.FullSummaryButtonClick;
import com.coreeng.supportbot.testkit.FullSummaryForm;
import com.coreeng.supportbot.testkit.FullSummaryFormSubmission;
import com.coreeng.supportbot.testkit.EscalationForm;
import com.coreeng.supportbot.testkit.EscalationFormSubmission;
import com.coreeng.supportbot.testkit.EscalationMessage;
import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.MessageUpdatedExpectation;
import com.coreeng.supportbot.testkit.ReactionAddedExpectation;
import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.Stub;
import com.coreeng.supportbot.testkit.StubWithResult;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.ThreadMessagePostedExpectation;
import com.coreeng.supportbot.testkit.Ticket;
import com.coreeng.supportbot.testkit.TicketMessage;
import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.supportBot;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import com.google.common.collect.ImmutableList;

@ExtendWith(TestKitExtension.class)
public class TicketManagementTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;
    private SlackWiremock slackWiremock;
    private Config config;

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
        ticket.applyChangesLocally().applyFormValues(values);
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
    }

    @Test
    public void whenTicketIsClosedByUsingFullSummaryForm_ticketIsMarkedAsClosedInDatabase() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t
            .queryTs(queryTs)
            .createdMessageTs(MessageTs.now())
        );

        // Stub the chat.update call that happens when ticket status is updated
        StubWithResult<TicketMessage> updatedTicketMessageStub = slackWiremock.stubMessageUpdated(
            MessageUpdatedExpectation.<TicketMessage>builder()
                .channelId(ticket.channelId())
                .ts(ticket.formMessageTs())
                .threadTs(queryTs)
                .receiver(new TicketMessage.Receiver())
                .build()
        );

        // Stub the white_check_mark reaction that gets added to the query message when ticket is closed
        Stub whiteCheckMarkReactionStub = slackWiremock.stubReactionAdd(
            ReactionAddedExpectation.builder()
                .reaction("white_check_mark")
                .channelId(ticket.channelId())
                .ts(ticket.queryTs())
                .build()
        );

        // when
        String openFullSummaryTriggerId = "whenFullSummaryButtonIsClicked_formIsOpened";
        StubWithResult<FullSummaryForm> fullSummaryFormOpenedExpectation = ticket.expectFullSummaryFormOpened(openFullSummaryTriggerId);
        FullSummaryButtonClick fullSummaryClick = ticket.fullSummaryButtonClick(openFullSummaryTriggerId);
        asSupport.slack().clickMessageButton(fullSummaryClick);
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(fullSummaryFormOpenedExpectation::assertIsCalled);

        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
            .status("closed")
            .team("connected-app")
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .impact("bauBlocking")
            .build();
        asSupport.slack().submitView(ticket.fullSummaryFormSubmit(
            openFullSummaryTriggerId,
            values));

        // then
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                updatedTicketMessageStub.assertIsCalled();
                whiteCheckMarkReactionStub.assertIsCalled();
            });
        
        ticket.applyChangesLocally()
            .applyFormValues(values)
            .addLog("closed");
        
        // Verify the ticket status is updated in the database
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        updatedTicketMessageStub.result().assertMatches(ticketResponse);
    }

    @Test
    public void whenTicketIsEscalated_escalationIsCreatedInDatabase() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        Ticket ticket = asSupport.ticket().create(t -> t
            .queryTs(MessageTs.now())
            .createdMessageTs(MessageTs.now())
        );

        Config.EscalationTeam escalationTeam = config.escalationTeams().getFirst();

        String openEscalationTriggerId = "whenEscalateButtonIsClicked_formIsOpened";
        StubWithResult<EscalationForm> escalationFormOpenedStub = ticket.expectEscalationFormOpened(openEscalationTriggerId);
        StubWithResult<EscalationMessage> escalationMessageCreatedStub = ticket.expectEscalationMessagePosted(escalationTeam.slackGroupId(), MessageTs.now());
        // Stub the rocket reaction added to the query thread after escalation
        Stub rocketReactionAddedStub = slackWiremock.stubReactionAdd(
            ReactionAddedExpectation.builder()
                .reaction("rocket")
                .channelId(ticket.channelId())
                .ts(ticket.queryTs())
                .build()
        );

        // when
        asSupport.slack().clickMessageButton(ticket.escalateButtonClick(openEscalationTriggerId));
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(escalationFormOpenedStub::assertIsCalled);

        EscalationFormSubmission.Values values = EscalationFormSubmission.Values.builder()
            .team(escalationTeam.name())
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .build();
        asSupport.slack().submitView(ticket.escalationFormSubmit(openEscalationTriggerId, values));

        ticket.applyChangesLocally()
            .applyEscalationFromValues(values);

        // then
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                escalationMessageCreatedStub.assertIsCalled();
                rocketReactionAddedStub.assertIsCalled();
            });
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        escalationMessageCreatedStub.result().assertMatches(ticketResponse);
    }

    public void whenEscalatedTicketIsClosed_warningDisplayedAndEscalationsAreClosed() {

    }
}
