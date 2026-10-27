package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

/** A click on one of the star buttons in the ephemeral rating request. */
@Builder
@Getter
public class RatingButtonClick {
    @NonNull private final String triggerId;

    @NonNull private final Ticket ticket;

    private final int rating;

    public String actionId() {
        return "rating-submit-" + rating;
    }

    public String privateMetadata() {
        return String.format("{\"ticketId\":%d,\"rating\":%d}", ticket.id(), rating);
    }
}
