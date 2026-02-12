package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static com.coreeng.supportbot.testkit.UserRole.workflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coreeng.supportbot.testkit.Config;
import com.coreeng.supportbot.testkit.EscalationFormSubmission;
import com.coreeng.supportbot.testkit.FullSummaryFormSubmission;
import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.Stub;
import com.coreeng.supportbot.testkit.SummaryCloseConfirm;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import com.coreeng.supportbot.testkit.Ticket;
import com.coreeng.supportbot.testkit.TicketByIdQuery;
import com.coreeng.supportbot.testkit.TicketMessage;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
public class TicketManagementTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;
    private Config config;

    @Test
    public void whenTicketHasMultipleEscalations_summaryShowsAllAndCloseResolvesAll() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t.queryTs(queryTs).createdMessageTs(MessageTs.now()));

        var teams = config.escalationTeams();
        Config.EscalationTeam firstTeam = teams.get(0);
        Config.EscalationTeam secondTeam = teams.get(1);

        ImmutableList<@NonNull String> firstTags = ImmutableList.of("jenkins-pipelines", "networking");
        ImmutableList<@NonNull String> secondTags = ImmutableList.of("ingresses", "networking");

        ticket.escalateViaTestApi(MessageTs.now(), firstTeam.code(), firstTags);
        ticket.escalateViaTestApi(MessageTs.now(), secondTeam.code(), secondTags);

        // when: open Full Summary and assert full structure (including both escalations)
        String openFullSummaryTriggerId = "summary_open_multi";
        Ticket.FullSummaryFormOpenStubs summaryFormOpened =
                ticket.expectFullSummaryFormOpened("full summary view opened", openFullSummaryTriggerId);
        asSupport.slack().clickMessageButton(ticket.fullSummaryButtonClick(openFullSummaryTriggerId));
        summaryFormOpened.awaitAllCalled(Duration.ofSeconds(5));

        FullSummaryFormSubmission.Values closeValues = FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.closed)
                .team("connected-app")
                .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                .impact("productionBlocking")
                .build();

        var closeStubs = ticket.stubCloseFlow("ticket close");
        SummaryCloseConfirm confirm = asSupport
                .slack()
                .submitView(
                        ticket.fullSummaryFormSubmit(openFullSummaryTriggerId, closeValues),
                        new SummaryCloseConfirm.Receiver(ticket.id(), 2))
                .assertMatches(closeValues);

        // confirm closing
        asSupport.slack().submitView(confirm.toSubmission(openFullSummaryTriggerId + "_confirm"));

        ticket.applyChangesLocally()
                .applyFormValues(closeValues)
                .addLog("closed")
                .resolveEscalations();

        // then
        closeStubs.awaitAllCalled(Duration.ofSeconds(5));

        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        ticket.assertMatches(ticketResponse);
        closeStubs.messageUpdated().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenSupportReactedWithEyes_ticketRegistered() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        TestKit.RoledTestKit asSupport = testKit.as(support);
        SlackTestKit asSupportSlack = asSupport.slack();
        var supportUser = config.supportUsers().getFirst();

        // when
        String queryText = "Please, help me with my query";
        SlackMessage tenantsMessage = asTenantSlack.postMessage(MessageTs.now(), queryText);
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsMessage.stubTicketCreationFlow("ticket created", ticketMessageTs);

        asSupportSlack.addReactionTo(tenantsMessage, "eyes");

        // then
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));
        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();
        var ticketResponse =
                supportBotClient.assertTicketExists(TicketByIdQuery.fromTicketMessage(ticketMessage, queryText));
        ticketMessage.assertMatches(ticketResponse);

        assertThat(ticketResponse.assignedTo())
                .as("Ticket should be auto-assigned to support member who reacted with eyes emoji")
                .isEqualTo(supportUser.email());
    }

    @Test
    public void whenQueryHasThreadReplyAndSupportReactsWithEyesToQuery_ticketIsCreated() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        // Step 1: Post a query
        MessageTs queryTs = MessageTs.now();
        String queryText = "Please, help me with my query";
        SlackMessage tenantsQuery = asTenantSlack.postMessage(queryTs, queryText);

        // Step 2: Post a thread reply to the query
        MessageTs replyTs = MessageTs.now();
        asTenantSlack.postThreadReply(replyTs, queryTs, "Here is some additional information about my issue");

        // Step 3: Set up stubs for ticket creation on the query message
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow("ticket created", ticketMessageTs);

        // Step 4: Support reacts with eyes to the query message (not the thread reply)
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");

        // then: verify ticket was created successfully
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));
        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();
        var ticketResponse =
                supportBotClient.assertTicketExists(TicketByIdQuery.fromTicketMessage(ticketMessage, queryText));
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
        SlackMessage tenantsQuery = asTenantSlack.postMessage(queryTs, "Please, help me with my query");

        // Step 2: Support reacts with eyes to create a ticket
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow("ticket created", ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));

        // Verify the original ticket form was created
        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();

        // Step 3: Post a message in the query thread (a reply)
        MessageTs replyTs = MessageTs.now();
        SlackMessage threadReply =
                asTenantSlack.postThreadReply(replyTs, queryTs, "Here is some additional information about my issue");

        // Step 4: Support reacts with eyes to the thread reply
        MessageTs secondTicketMessageTs = MessageTs.now();
        var buggyCreationStubs = threadReply.stubTicketCreationFlow("buggy ticket creation", secondTicketMessageTs);

        // Override the conversations.replies stub to indicate this IS a thread reply (thread_ts != ts)
        Stub threadReplyCheck = threadReply.stubAsThreadReply("thread reply check", queryTs);
        // Slack doesn't provide thread context when notifies about added reaction
        asSupportSlack.addReactionTo(threadReply, "eyes");

        await().pollDelay(Duration.ofSeconds(1)).untilAsserted(() -> {
            threadReplyCheck.assertIsCalled();

            buggyCreationStubs.assertNotCalled();
        });
        buggyCreationStubs.cleanUp();
    }

    @Test
    public void whenTicketIsEditedByUsingFullSummaryForm_changesAreSavedToADatabase() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        Ticket ticket =
                asSupport.ticket().create(t -> t.queryTs(MessageTs.now()).createdMessageTs(MessageTs.now()));

        // when
        String openFullSummaryTriggerId = "summary_open";
        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.opened)
                .team("wow")
                .tags(ImmutableList.of("ingresses", "networking"))
                .impact("productionBlocking")
                .build();
        ticket.openSummaryAndSubmit(asSupport.slack(), "full summary form", openFullSummaryTriggerId, values);

        // then
        ticket.applyChangesLocally().applyFormValues(values);
        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        ticket.assertMatches(ticketResponse);
    }

    @Test
    public void whenTicketIsClosedByUsingFullSummaryForm_ticketIsMarkedAsClosedInDatabase() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t.queryTs(queryTs).createdMessageTs(MessageTs.now()));

        // when
        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.closed)
                .team("connected-app")
                .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                .impact("bauBlocking")
                .build();
        var closeStubs = ticket.openSummaryAndSubmit(
                asSupport.slack(), "summary_open", "summary_open", values, () -> ticket.stubCloseFlow("ticket close"));

        // then
        closeStubs.awaitAllCalled(Duration.ofSeconds(5));

        ticket.applyChangesLocally().applyFormValues(values).addLog("closed");

        // Verify the ticket status is updated in the database
        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        ticket.assertMatches(ticketResponse);
        closeStubs.messageUpdated().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenTicketIsEscalated_escalationIsCreatedInDatabase() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        Ticket ticket =
                asSupport.ticket().create(t -> t.queryTs(MessageTs.now()).createdMessageTs(MessageTs.now()));

        Config.EscalationTeam escalationTeam = config.escalationTeams().getFirst();
        String openEscalationTriggerId = "escalate_open";
        var escalateStubs = ticket.stubEscalateFlow("ticket escalate", escalationTeam.slackGroupId(), MessageTs.now());

        // when
        ticket.openEscalationAndSubmit(
                asSupport.slack(),
                "escalation form",
                openEscalationTriggerId,
                EscalationFormSubmission.Values.builder()
                        .team(escalationTeam.code())
                        .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                        .build());

        EscalationFormSubmission.Values values = EscalationFormSubmission.Values.builder()
                .team(escalationTeam.code())
                .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                .build();

        ticket.applyChangesLocally().applyEscalationFromValues(values);

        // then
        escalateStubs.awaitAllCalled(Duration.ofSeconds(5));
        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        ticket.assertMatches(ticketResponse);
        escalateStubs.escalationMessage().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenEscalatedTicketIsClosed_warningDisplayedAndEscalationsAreClosed() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t.queryTs(queryTs).createdMessageTs(MessageTs.now()));

        // Escalate via test controller
        ImmutableList<@NonNull String> escalationTags = ImmutableList.of("jenkins-pipelines", "networking");
        ticket.escalateViaTestApi(
                MessageTs.now(), config.escalationTeams().getFirst().code(), escalationTags);

        // Stub Slack updates for closing (composite)
        var closeStubsWhenEscalatedClosed = ticket.stubCloseFlow("ticket close");

        // Open summary view and submit, expect a close confirmation
        String openFullSummaryTriggerId = "summary_open";
        FullSummaryFormSubmission.Values values = FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.closed)
                .team("connected-app")
                .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                .impact("productionBlocking")
                .build();

        SummaryCloseConfirm confirm = asSupport
                .slack()
                .submitView(
                        ticket.fullSummaryFormSubmit(openFullSummaryTriggerId, values),
                        new SummaryCloseConfirm.Receiver(ticket.id(), 1))
                .assertMatches(values);

        // Submit SummaryCloseConfirm via submitView
        asSupport.slack().submitView(confirm.toSubmission(openFullSummaryTriggerId + "_confirm"));

        ticket.applyChangesLocally().applyFormValues(values).addLog("closed").resolveEscalations();

        // then
        closeStubsWhenEscalatedClosed.awaitAllCalled(Duration.ofSeconds(5));

        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        ticket.assertMatches(ticketResponse);
        closeStubsWhenEscalatedClosed.messageUpdated().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenClosedTicketIsReopened_ticketIsMarkedAsOpenedAndCheckmarkRemoved() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        MessageTs queryTs = MessageTs.now();
        Ticket ticket = asSupport.ticket().create(t -> t.queryTs(queryTs).createdMessageTs(MessageTs.now()));

        // when: close via full summary
        String triggerClose = "summary_open_close";
        FullSummaryFormSubmission.Values closeValues = FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.closed)
                .team("connected-app")
                .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                .impact("bauBlocking")
                .build();
        var closeStubs = ticket.openSummaryAndSubmit(
                asSupport.slack(),
                "ticket close",
                triggerClose,
                closeValues,
                () -> ticket.stubCloseFlow("ticket close"));

        ticket.applyChangesLocally().applyFormValues(closeValues).addLog("closed");

        closeStubs.awaitAllCalled(Duration.ofSeconds(5));

        // when: reopen via full summary
        String triggerReopen = "summary_open_reopen";
        FullSummaryFormSubmission.Values reopenValues = FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.opened)
                .team("connected-app")
                .tags(ImmutableList.of("jenkins-pipelines", "networking"))
                .impact("productionBlocking")
                .build();
        var reopenStubs = ticket.openSummaryAndSubmit(
                asSupport.slack(),
                "ticket reopen",
                triggerReopen,
                reopenValues,
                () -> ticket.stubReopenFlow("ticket reopen"));

        ticket.applyChangesLocally().applyFormValues(reopenValues).addLog("opened");

        // then
        reopenStubs.awaitAllCalled(Duration.ofSeconds(5));

        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        ticket.assertMatches(ticketResponse);
        reopenStubs.messageUpdated().result().assertMatches(ticketResponse);
    }

    @Test
    public void whenQueryAuthorIsBot_teamSuggestionsStillWork() {
        // given: Create a ticket where the author is a bot
        TestKit.RoledTestKit asBot = testKit.as(workflow);
        Ticket ticket = asBot.ticket().create(t -> t.queryTs(MessageTs.now())
                .createdMessageTs(MessageTs.now())
                .message("Automated alert from bot"));

        SlackTestKit asSupportSlack = testKit.as(support).slack();

        // when: Open the full summary form
        String triggerId = "summary_open_bot";
        Ticket.FullSummaryFormOpenStubs summaryFormOpened =
                ticket.expectFullSummaryFormOpened("full summary opened", triggerId);
        asSupportSlack.clickMessageButton(ticket.fullSummaryButtonClick(triggerId));
        summaryFormOpened.awaitAllCalled(Duration.ofSeconds(5));

        // when: Request team suggestions
        var response = asSupportSlack.requestBlockSuggestion(ticket.teamSuggestionRequest());

        // then: Should return 200 with team options
        response.statusCode(200);
    }

    @Test
    public void whenTicketIsManuallyReassignedViaFullSummary_newAssigneeIsSaved() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        TestKit.RoledTestKit asSupport = testKit.as(support);
        String queryMessage = "Please, help me with my query";
        Ticket ticket = asTenant.ticket().create(builder -> builder.message(queryMessage));
        var supportUser = config.supportUsers().getFirst();

        String triggerId = "reassign_ticket_trigger";
        FullSummaryFormSubmission.Values reassignValues = FullSummaryFormSubmission.Values.builder()
                .status(ticket.status())
                .team("wow")
                .tags(ImmutableList.of("ingresses"))
                .impact("productionBlocking")
                .assignedTo(supportUser.slackUserId())
                .build();

        ticket.openSummaryAndSubmit(asSupport.slack(), "ticket reassigned", triggerId, reassignValues);
        ticket.applyChangesLocally().applyFormValues(reassignValues);

        SupportBotClient.TicketResponse finalResponse =
                supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        assertThat(finalResponse.assignedTo())
                .as("Ticket should be assigned to support member")
                .isEqualTo(supportUser.email());
    }
}
