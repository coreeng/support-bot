package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class FullSummaryButtonClick implements MessageButtonClick {
    @NonNull private final String triggerId;

    @NonNull private final Long ticketId;

    @Override
    public String privateMetadata() {
        return String.format("""
            {"ticketId": %d}""", ticketId);
    }

    @Override
    public String actionId() {
        return TicketMessage.FULL_SUMMARY_BUTTON_ACTION_ID;
    }
}
