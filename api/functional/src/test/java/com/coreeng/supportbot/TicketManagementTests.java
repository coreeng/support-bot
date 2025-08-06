package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.Stub;
import com.coreeng.supportbot.testkit.StubWithResult;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.ThreadMessagePostedExpectation;
import com.coreeng.supportbot.testkit.TicketMessage;
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

    @Test
    public void whenQueryIsPosted_thenQueryIsRegisteredInBot() {
        // given
        SlackTestKit asTenant = testKit.as(tenant).slack();

        // when
        SlackMessage tenantsMessage = asTenant.postMessage("Please, help me with my query");

        // then
        supportBotClient.assertQueryExistsByMessageRef(tenantsMessage.channelId(), tenantsMessage.ts());
    }

    @Test
    public void whenSupportReactedWithEyes_ticketRegistered() {
        // given
        SlackTestKit asTenant = testKit.as(tenant).slack();
        SlackTestKit asSupport = testKit.as(support).slack();

        // when
        SlackMessage tenantsMessage = asTenant.postMessage("Please, help me with my query");
        Stub ticketReactionAddedStub = tenantsMessage.expectReactionAdded("ticket");
        String ticketMessageTs = SlackMessage.generateNewTs();
        StubWithResult<TicketMessage> ticketMessagePostedStub = tenantsMessage.expectThreadMessagePosted(ThreadMessagePostedExpectation.<TicketMessage>builder()
            .receiver(new TicketMessage.Receiver())
            .from(supportBot)
            .newMessageTs(ticketMessageTs)
            .build());

        asSupport.addReactionTo(tenantsMessage, "eyes");

        // then
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                ticketReactionAddedStub.assertIsCalled();
                ticketMessagePostedStub.assertIsCalled();
            });
        TicketMessage ticketMessage = ticketMessagePostedStub.result();
        assertThat(ticketMessage).isNotNull();
        supportBotClient.assertTicketExists(ticketMessage);
    }

    @Test
    public void whenTicketIsEditedByUsingFullSummaryForm_changesAreSavedToADatabase() {

    }
}
