package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;

import lombok.Builder;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

@Builder
@Getter
public class FullSummaryButtonClick implements MessageButtonClick {
    @NonNull
    private final String triggerId;
    @NonNull
    private final String actionId;
    @NonNull
    private final Ticket ticket;

    @NonNull
    private final SlackWiremock slackWiremock;

    @Override
    public String privateMetadata() {
        return String.format("""
            {"ticketId": %d}""", ticket.id());
    }

    @Override
    public void preSetupMocks() {
        slackWiremock.stubGetPermalink(ticket.channelId(), ticket.queryTs());
        slackWiremock.stubGetMessage(MessageToGet.builder()
            .channelId(ticket.channelId())
            .ts(ticket.queryTs())
            .threadTs(ticket.queryTs())
            .blocksJson(ticket.queryBlocksJson())
            .userId(ticket.user().slackUserId())
            .botId(ticket.user().slackBotId())
            .team(ticket.teamId())
            .build());

        // Only stub user profile if user is not a bot
        if (ticket.user().isHuman()) {
            slackWiremock.stubGetUserProfileById(UserProfileToGet.builder()
                .userId(requireNonNull(ticket.user().slackUserId()))
                .email(requireNonNull(ticket.user().email()))
                .build());
        }
    }
}
