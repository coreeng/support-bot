package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;

import lombok.Builder;
import lombok.Getter;

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
            .user(ticket.user().slackUserId())
            .team(ticket.teamId())
            .build());
        slackWiremock.stubGetUserProfileById(UserProfileToGet.builder()
            .userId(ticket.user().slackUserId())
            .email(ticket.user().email())
            .build());
    }
}
