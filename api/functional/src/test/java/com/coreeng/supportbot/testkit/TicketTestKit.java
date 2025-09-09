package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.Config;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
public class TicketTestKit {
    private final TestKit.@NonNull RoledTestKit testKit;
    @NonNull
    private final SupportBotClient supportBotClient;
    @NonNull
    private final SlackWiremock slackWiremock;
    @NonNull
    private final Config config;

    private static String messageToBlocksJson(String message) {
        return String.format("""
            [{"type":"rich_text","elements":[{"type":"rich_text_section","elements":[{"type":"text","text":"%s"}]}]}]
            """, message);
    }

    public Ticket create(UnaryOperator<TicketToCreate.TicketToCreateBuilder> updateTicketToCreate) {
        TicketToCreate ticketToCreate = updateTicketToCreate.apply(TicketToCreate.builder()
            .channelId(testKit.channelId())
        ).build();

        slackWiremock.stubGetPermalink(ticketToCreate.channelId(), ticketToCreate.queryTs());
        SupportBotClient.TicketResponse response = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
            .channelId(ticketToCreate.channelId())
            .queryTs(ticketToCreate.queryTs())
            .createdMessageTs(ticketToCreate.createdMessageTs())
            .build());

        return Ticket.fromResponse(response)
            .user(requireNonNull(config.userById(testKit.userId())))
            .config(config)
            .teamId(testKit.teamId())
            .slackWiremock(slackWiremock)
            .supportBotClient(supportBotClient)
            .queryBlocksJson(messageToBlocksJson(ticketToCreate.message()))
            .queryPermalink("https://slack.com/messages/" + testKit.channelId() + "/" + ticketToCreate.queryTs())
            .build();
    }
}
