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
    @Nullable
    private final String authorId;
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
        if (authorId == null) {
            return String.format("""
                {"ticketId": %d, "authorId": null}
                """, ticketId);
        }
        return String.format("""
            {"ticketId": %d, "authorId": "%s"}
            """, ticketId, authorId);
    }

    @Override
    public String callbackId() {
        return "ticket-summary";
    }
}

