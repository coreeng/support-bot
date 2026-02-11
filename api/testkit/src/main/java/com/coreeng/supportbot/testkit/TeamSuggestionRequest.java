package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Request for team suggestions in the full summary form.
 * This simulates the Slack block_suggestion event when user types in the team selector.
 */
@Builder
@Getter
public class TeamSuggestionRequest implements BlockSuggestionRequest {
    private final long ticketId;

    @Nullable private final String userId;

    @Nullable private final String botId;

    @Builder.Default
    private final String filterValue = "";

    @Override
    public String actionId() {
        return "ticket-change-team";
    }

    @Override
    public String value() {
        return filterValue;
    }

    @Override
    public String viewType() {
        return "modal";
    }

    @Override
    public String privateMetadata() {
        if (userId == null && botId == null) {
            return String.format("""
                {"ticketId": %d, "authorId": null}
                """, ticketId);
        }

        String authorType = userId == null ? "bot" : "user";
        String authorId = userId == null ? botId : userId;
        return String.format("""
            {"ticketId": %d, "authorId": {"type": "%s", "id": "%s"}}
            """, ticketId, authorType, authorId);
    }

    @Override
    public String callbackId() {
        return "ticket-summary";
    }
}
