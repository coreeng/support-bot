package com.coreeng.supportbot.testkit;


import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public class TicketTestKit {
    private final TestKit.@NonNull RoledTestKit testKit;
    @NonNull
    private final SupportBotClient supportBotClient;
    @NonNull
    private final SlackWiremock slackWiremock;
    @NonNull
    private final Config config;

    public static String messageToBlocksJson(String message) {
        return String.format("""
            [{"type":"rich_text","elements":[{"type":"rich_text_section","elements":[{"type":"text","text":"%s"}]}]}]
            """, message);
    }

    public Ticket create(UnaryOperator<TicketToCreate.TicketToCreateBuilder> updateTicketToCreate) {
        TicketToCreate ticketToCreate = updateTicketToCreate.apply(TicketToCreate.builder()
            .channelId(testKit.channelId())
            .queryTs(MessageTs.now())
            .createdMessageTs(MessageTs.now())
        ).build();

        Stub getPermalinkStub = slackWiremock.stubGetPermalink(ticketToCreate.opDescription() + ": get permalink", ticketToCreate.channelId(), ticketToCreate.queryTs());
        Stub getMessageStub = slackWiremock.stubGetMessage(MessageToGet.builder()
            .description(ticketToCreate.opDescription() + ": get message")
            .channelId(ticketToCreate.channelId())
            .ts(ticketToCreate.queryTs())
            .threadTs(ticketToCreate.queryTs())
            .text(ticketToCreate.message())
            .blocksJson(messageToBlocksJson(ticketToCreate.message()))
            .userId(testKit.user().slackUserId())
            .botId(testKit.user().slackBotId())
            .build());
        SupportBotClient.TicketResponse response = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
            .channelId(ticketToCreate.channelId())
            .queryTs(ticketToCreate.queryTs())
            .createdMessageTs(ticketToCreate.createdMessageTs())
            .build());

        getPermalinkStub.assertIsCalled();
        getMessageStub.assertIsCalled();

        return Ticket.fromResponse(response)
            .user(testKit.user())
            .config(config)
            .teamId(testKit.teamId())
            .slackWiremock(slackWiremock)
            .supportBotClient(supportBotClient)
            .queryBlocksJson(messageToBlocksJson(ticketToCreate.message()))
            .queryText(ticketToCreate.message())
            .queryPermalink("https://slack.com/messages/" + testKit.channelId() + "/" + ticketToCreate.queryTs())
            .build();
    }
}
