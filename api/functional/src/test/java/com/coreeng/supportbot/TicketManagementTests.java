package com.coreeng.supportbot;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coreeng.supportbot.testkit.EscalationFormSubmission;
import com.coreeng.supportbot.testkit.FullSummaryForm;
import com.coreeng.supportbot.testkit.FullSummaryFormSubmission;
import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.StubWithResult;
import com.coreeng.supportbot.testkit.SummaryCloseConfirm;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.Ticket;
import com.coreeng.supportbot.testkit.TicketMessage;
import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import com.google.common.collect.ImmutableList;

@ExtendWith(TestKitExtension.class)
public class TicketManagementTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;
    private Config config;

    @Test
    public void whenQueryIsPosted_thenQueryIsRegisteredInBot() {
        // given
        SlackTestKit asTenant = testKit.as(tenant).slack();

        // when
        SlackMessage tenantsMessage = asTenant.postMessage(
            MessageTs.now(),
            "Please, help me with my query!"
        );

        // then
        supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts());
    }

    @Test
    public void whenTicketHasMultipleEscalations_summaryShowsAllAndCloseResolvesAll() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t
            .queryTs(queryTs)
            .createdMessageTs(MessageTs.now())
        );

        var teams = config.escalationTeams();
        Config.EscalationTeam firstTeam = teams.get(0);
        Config.EscalationTeam secondTeam = teams.get(1);

        ImmutableList<@NonNull String> firstTags = ImmutableList.of("jenkins-pipelines", "networking");
        ImmutableList<@NonNull String> secondTags = ImmutableList.of("ingresses", "networking");

        ticket.escalateViaTestApi(MessageTs.now(), firstTeam.code(), firstTags);
        ticket.escalateViaTestApi(MessageTs.now(), secondTeam.code(), secondTags);

        var closeStubs = ticket.stubCloseFlow(queryTs);

        // when: open Full Summary and assert full structure (including both escalations)
        String openFullSummaryTriggerId = "summary_open_multi";
        StubWithResult<FullSummaryForm> summaryFormOpened = ticket.expectFullSummaryFormOpened(openFullSummaryTriggerId);
        asSupport.slack().clickMessageButton(ticket.fullSummaryButtonClick(openFullSummaryTriggerId));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            summaryFormOpened.assertIsCalled("full summary form opened")
        );

        FullSummaryFormSubmission.Values closeValues = FullSummaryFormSubmission.Values.builder()
            .status(Ticket.Status.closed)
            .team("connected-app")
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .impact("productionBlocking")
            .build();

        SummaryCloseConfirm confirm = asSupport.slack().submitView(
            ticket.fullSummaryFormSubmit(openFullSummaryTriggerId, closeValues),
            new SummaryCloseConfirm.Receiver(ticket.id(), 2)
        ).assertMatches(closeValues);

        // confirm closing
        asSupport.slack().submitView(confirm.toSubmission(openFullSummaryTriggerId + "_confirm"));

        ticket.applyChangesLocally()
            .applyFormValues(closeValues)
            .addLog("closed")
            .resolveEscalations();

        // then
        closeStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket close");

        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        closeStubs.messageUpdated().result().assertMatches(ticketResponse);
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
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsMessage.stubTicketCreationFlow(ticketMessageTs);

        asSupportSlack.addReactionTo(tenantsMessage, "eyes");

        // then
        creationStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket created");
        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();
        var ticketResponse = supportBotClient.assertTicketExists(ticketMessage);
        ticketMessage.assertMatches(ticketResponse);
    }

    @Test
    public void whenSupportReactedWithEyesToThreadReply_noAdditionalTicketFormIsCreated() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        // Step 1: Post a query
        MessageTs queryTs = MessageTs.now();
        SlackMessage tenantsQuery = asTenantSlack.postMessage(
            queryTs,
            "Please, help me with my query"
        );

        // Step 2: Support reacts with eyes to create a ticket
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow(ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");
        creationStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket created");

        // Verify the original ticket form was created
        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();

        // Step 3: Post a message in the query thread (a reply)
        MessageTs replyTs = MessageTs.now();
        SlackMessage threadReply = asTenantSlack.postThreadReply(
            replyTs,
            queryTs,
            "Here is some additional information about my issue"
        );

        // Step 4: Support reacts with eyes to the thread reply
        MessageTs secondTicketMessageTs = MessageTs.now();
        var buggyCreationStubs = threadReply.stubTicketCreationFlow(secondTicketMessageTs);

        // Override the conversations.replies stub to indicate this IS a thread reply (thread_ts != ts)
        threadReply.stubAsThreadReply(queryTs);

        // Slack doesn't provide thread context when notifies about added reaction
        asSupportSlack.addReactionTo(threadReply, "eyes");

        await().pollDelay(Duration.ofSeconds(1)).untilAsserted(() -> {
            buggyCreationStubs.reactionAdded().assertIsNotCalled(
                "Reaction added event is received for eyes reaction on thread reply"
            );
            buggyCreationStubs.ticketMessagePosted().assertIsNotCalled(
                "No ticket form should be created for eyes reaction on thread reply"
            );
        });
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
        String openFullSummaryTriggerId = "summary_open";
        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
            .status(Ticket.Status.opened)
            .team("wow")
            .tags(ImmutableList.of("ingresses", "networking"))
            .impact("productionBlocking")
            .build();
        ticket.openSummaryAndSubmit(asSupport.slack(), openFullSummaryTriggerId, values);

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

        var closeStubs = ticket.stubCloseFlow(queryTs);

        // when
        String openFullSummaryTriggerId = "summary_open";
        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
            .status(Ticket.Status.closed)
            .team("connected-app")
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .impact("bauBlocking")
            .build();
        ticket.openSummaryAndSubmit(asSupport.slack(), openFullSummaryTriggerId, values);

        // then
        closeStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket close");

        ticket.applyChangesLocally()
            .applyFormValues(values)
            .addLog("closed");

        // Verify the ticket status is updated in the database
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        closeStubs.messageUpdated().result().assertMatches(ticketResponse);
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
        String openEscalationTriggerId = "escalate_open";
        var escalateStubs = ticket.stubEscalateFlow(escalationTeam.slackGroupId(), MessageTs.now());

        // when
        ticket.openEscalationAndSubmit(asSupport.slack(), openEscalationTriggerId, EscalationFormSubmission.Values.builder()
            .team(escalationTeam.code())
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .build());

        EscalationFormSubmission.Values values = EscalationFormSubmission.Values.builder()
            .team(escalationTeam.code())
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .build();

        ticket.applyChangesLocally()
            .applyEscalationFromValues(values);

        // then
        escalateStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket escalate");
        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        escalateStubs.escalationMessage().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenEscalatedTicketIsClosed_warningDisplayedAndEscalationsAreClosed() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t
            .queryTs(queryTs)
            .createdMessageTs(MessageTs.now())
        );

        // Escalate via test controller
        ImmutableList<@NonNull String> escalationTags = ImmutableList.of("jenkins-pipelines", "networking");
        ticket.escalateViaTestApi(MessageTs.now(), config.escalationTeams().getFirst().code(), escalationTags);

        // Stub Slack updates for closing (composite)
        var closeStubs_whenEscalatedClosed = ticket.stubCloseFlow(queryTs);

        // Open summary view and submit, expect a close confirmation
        String openFullSummaryTriggerId = "summary_open";
        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
            .status(Ticket.Status.closed)
            .team("connected-app")
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .impact("productionBlocking")
            .build();

        SummaryCloseConfirm confirm = asSupport.slack().submitView(
            ticket.fullSummaryFormSubmit(openFullSummaryTriggerId, values),
            new SummaryCloseConfirm.Receiver(ticket.id(), 1)
        ).assertMatches(values);

        // Submit SummaryCloseConfirm via submitView
        asSupport.slack().submitView(confirm.toSubmission(openFullSummaryTriggerId + "_confirm"));

        ticket.applyChangesLocally()
            .applyFormValues(values)
            .addLog("closed")
            .resolveEscalations();

        // then
        closeStubs_whenEscalatedClosed.awaitAllCalled(Duration.ofSeconds(5), "ticket close");

        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        closeStubs_whenEscalatedClosed.messageUpdated().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenClosedTicketIsReopened_ticketIsMarkedAsOpenedAndCheckmarkRemoved() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t
            .queryTs(queryTs)
            .createdMessageTs(MessageTs.now())
        );

        var closeStubs = ticket.stubCloseFlow(queryTs);

        // when: close via full summary
        String triggerClose = "summary_open_close";
        FullSummaryFormSubmission.Values closeValues = FullSummaryFormSubmission.Values.builder()
            .status(Ticket.Status.closed)
            .team("connected-app")
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .impact("bauBlocking")
            .build();
        ticket.openSummaryAndSubmit(asSupport.slack(), triggerClose, closeValues);

        ticket.applyChangesLocally()
            .applyFormValues(closeValues)
            .addLog("closed");

        closeStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket close");

        var reopenStubs = ticket.stubReopenFlow(queryTs);

        // when: reopen via full summary
        String triggerReopen = "summary_open_reopen";
        FullSummaryFormSubmission.Values reopenValues = FullSummaryFormSubmission.Values.builder()
            .status(Ticket.Status.opened)
            .team("connected-app")
            .tags(ImmutableList.of("jenkins-pipelines", "networking"))
            .impact("productionBlocking")
            .build();
        ticket.openSummaryAndSubmit(asSupport.slack(), triggerReopen, reopenValues);

        ticket.applyChangesLocally()
            .applyFormValues(reopenValues)
            .addLog("opened");

        // then
        reopenStubs.awaitAllCalled(Duration.ofSeconds(5), "ticket reopen");

        var ticketResponse = supportBotClient.assertTicketExists(ticket);
        ticket.assertMatches(ticketResponse);
        reopenStubs.messageUpdated().result().assertMatches(ticketResponse);
    }
}
