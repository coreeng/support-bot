package com.coreeng.supportbot;

import com.coreeng.supportbot.wiremock.SlackWiremock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WiremockInjectionExtension.class)
public class TicketManagementTests {
    private SlackWiremock slackWiremock;
    
    @Test
    public void whenMessageIsPostedAndEyesReactionIsAdded_thenTicketIsCreatedAndTicketReactionIsAdded() {
        var slackClient = new SupportBotSlackClient();

        slackWiremock.stubPostMessage();
        slackWiremock.stubReactionAdded();

        slackClient.notifyChannelMessagePosted();
        slackClient.notifyEyesOnTheQuery();
    }
}
