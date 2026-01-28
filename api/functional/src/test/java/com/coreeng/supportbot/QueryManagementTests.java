package com.coreeng.supportbot;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coreeng.supportbot.testkit.TestKitExtension;
import com.coreeng.supportbot.testkit.TicketByIdQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TicketMessage;
import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;

@ExtendWith(TestKitExtension.class)
public class QueryManagementTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;

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
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts())
        );
    }

    @Test
    public void whenQueryIsPostedAndDeletedWithoutTicket_thenQueryIsDeletedFromBot() {
        // given
        SlackTestKit asTenant = testKit.as(tenant).slack();

        // when: post a query
        SlackMessage tenantsMessage = asTenant.postMessage(
            MessageTs.now(),
            "Please, help me with my query!"
        );

        // then: query should exist
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts())
        );

        // when: delete the query message
        asTenant.deleteMessage(tenantsMessage);

        // then: query should no longer exist
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            supportBotClient.assertQueryDoesNotExistByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts())
        );
    }

    @Test
    public void whenThreadReplyIsDeleted_thenQueryStillExistsInBot() {
        // given
        SlackTestKit asTenant = testKit.as(tenant).slack();

        // when: post a query
        MessageTs queryTs = MessageTs.now();
        SlackMessage tenantsQuery = asTenant.postMessage(
            queryTs,
            "Please, help me with my query!"
        );

        // then: query should exist
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            supportBotClient.assertQueryExistsByMessageRef(tenantsQuery.channelId(), tenantsQuery.ts())
        );

        // when: post a thread reply
        MessageTs replyTs = MessageTs.now();
        SlackMessage threadReply = asTenant.postThreadReply(
            replyTs,
            queryTs,
            "Here is some additional information"
        );

        // when: delete the thread reply
        asTenant.deleteThreadReply(threadReply, queryTs);

        // then: query should still exist (only thread reply was deleted, not the query)
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            supportBotClient.assertQueryExistsByMessageRef(tenantsQuery.channelId(), tenantsQuery.ts())
        );
    }

    @Test
    public void whenQueryWithTicketIsDeleted_thenQueryAndTicketStillExistInBot() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        // when: post a query
        MessageTs queryTs = MessageTs.now();
        String queryMessage = "Please, help me with my query!";
        SlackMessage tenantsMessage = asTenantSlack.postMessage(queryTs, queryMessage);

        // then: query should exist
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts())
        );

        // when: support creates a ticket by reacting with eyes
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsMessage.stubTicketCreationFlow("ticket created", ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsMessage, "eyes");

        // then: ticket is created
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));
        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();
        TicketByIdQuery ticketByIdQuery = TicketByIdQuery.fromTicketMessage(ticketMessage, queryMessage);
        var ticketResponse = supportBotClient.assertTicketExists(ticketByIdQuery);
        ticketMessage.assertMatches(ticketResponse);

        // when: delete the query message
        asTenantSlack.deleteMessage(tenantsMessage);

        // then: query and ticket should still exist (ticket was already created)
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts());
            var ticketAfterDeletion = supportBotClient.assertTicketExists(ticketByIdQuery);
            ticketMessage.assertMatches(ticketAfterDeletion);
        });
    }
}

