package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.wiremock.SlackWiremock;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TicketTestKit {
    private final SlackWiremock slackWiremock;
    private final SupportBotClient supportBotClient;

//    public Ticket createTicket(TicketToCreate ticketToCreate) {
//
//    }
}
