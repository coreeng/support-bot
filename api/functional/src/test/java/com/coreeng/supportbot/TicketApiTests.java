package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.*;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(com.coreeng.supportbot.testkit.TestKitExtension.class)
public class TicketApiTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;
    private SlackWiremock slackWiremock;

    @Test
    public void whenTicketIsFetched_queryTextIsReturned() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        String messageText = "Functional test query text";

        // when
        Ticket ticket = asSupport.ticket().create(builder -> builder.message(messageText));

        // then
        var ticketResponse = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicket(ticket));
        assertThat(ticketResponse.query().text()).isEqualTo(messageText);
    }

    @Test
    public void whenTicketIsUpdated_viaApi_thenFieldsChange() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        Ticket ticket = asTenant.ticket().create(builder -> builder.message("Initial query"));

        var closeStubs = ticket.stubCloseFlow("ticket closed");

        // when
        var updated = supportBotClient.updateTicket(
                ticket.id(),
                SupportBotClient.UpdateTicketRequest.builder()
                        .status("closed")
                        .authorsTeam("wow")
                        .tags(ImmutableList.of("ingresses", "networking"))
                        .impact("productionBlocking")
                        .build());

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
        String dmChannelId = "D" + newAssignee;
        Stub openDmStub = slackWiremock.stubConversationsOpen("bulk reassign: open dm", newAssignee);
        Stub notifyStub = slackWiremock.stubChatPostMessage("bulk reassign: notify assignee", dmChannelId);

        // when
        var result = supportBotClient.bulkReassign(SupportBotClient.BulkReassignRequest.builder()
                .ticketIds(ImmutableList.of(ticket1.id(), ticket2.id(), ticket3.id()))
                .assignedTo(newAssignee)
                .build());

        // then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.successfulTicketIds()).containsExactlyInAnyOrder(ticket1.id(), ticket2.id(), ticket3.id());

        // Verify tickets are reassigned
        assertThat(supportBotClient
                        .assertTicketExists(TicketByIdQuery.fromTicket(ticket1))
                        .assignedTo())
                .isEqualTo("sana.chernobog@company.com");
        assertThat(supportBotClient
                        .assertTicketExists(TicketByIdQuery.fromTicket(ticket2))
                        .assignedTo())
                .isEqualTo("sana.chernobog@company.com");
        assertThat(supportBotClient
                        .assertTicketExists(TicketByIdQuery.fromTicket(ticket3))
                        .assignedTo())
                .isEqualTo("sana.chernobog@company.com");

        openDmStub.assertIsCalled();
        notifyStub.assertIsCalled();
    }

    @Test
    public void whenTeamSuggestionsAreFetched_groupedTeamsAreReturned() {
        // given
        TestKit.RoledTestKit asSupport = testKit.as(support);
        Ticket ticket = asSupport.ticket().create(builder -> builder.message("Team suggestions test"));

        // Stub the Slack message fetch that the team-suggestions endpoint will make
        Stub getMessageStub = slackWiremock.stubGetMessage(MessageToGet.builder()
                .description("team-suggestions: get message")
                .channelId(ticket.channelId())
                .ts(ticket.queryTs())
                .threadTs(ticket.queryTs())
                .text(ticket.queryText())
                .blocksJson(ticket.queryBlocksJson())
                .userId(asSupport.user().slackUserId())
                .build());

        // Stub users.info to return an email that maps to a known team
        Stub userProfileStub = slackWiremock.stubGetUserProfileById(UserProfileToGet.builder()
                .description("team-suggestions: get user profile")
                .userId(asSupport.user().slackUserId())
                .email(asSupport.user().email())
                .build());

        // when
        var suggestions = supportBotClient.getTeamSuggestions(ticket.id());

        // then
        assertThat(suggestions.suggestedTeams()).isNotNull();
        assertThat(suggestions.otherTeams()).isNotNull();
        // The author's email (coby.ivona@company.com) is in group-ref-wow → team wow
        assertThat(suggestions.suggestedTeams()).contains("wow");
        // Other teams should include the remaining static teams
        assertThat(suggestions.otherTeams()).containsAnyOf("infra-integration", "connected-app");
        // Suggested + others should cover all teams
        int totalTeams =
                suggestions.suggestedTeams().size() + suggestions.otherTeams().size();
        assertThat(totalTeams).isGreaterThanOrEqualTo(3);

        getMessageStub.assertIsCalled();
        userProfileStub.assertIsCalled();
    }

    @Test
    public void whenTeamSuggestionsAreFetchedForNonExistentTicket_returns404() {
        // when
        int statusCode = supportBotClient.getTeamSuggestionsStatusCode(999999L);

        // then
        assertThat(statusCode).isEqualTo(404);
    }
}
