package com.coreeng.supportbot.rating;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import javax.annotation.Nullable;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class TicketRating {
    @With
    @Nullable
    private UUID id;
    private Integer rating;
    private String submittedTs;
    private String status;
    private String anonymousId; // Hash of ticketId + userId for duplicate prevention without compromising anonymity
    @Nullable
    private String impact;
    @Nullable
    private String[] tags;
    private boolean isEscalated;

    public static TicketRating createNew(
        int rating,
        String submittedTs,
        String status,
        String anonymousId,
        @Nullable String impact,
        @Nullable String[] tags,
        boolean isEscalated
    ) {
        return TicketRating.builder()
            .rating(rating)
            .submittedTs(submittedTs)
            .status(status)
            .anonymousId(anonymousId)
            .impact(impact)
            .tags(tags)
            .isEscalated(isEscalated)
            .build();
    }

    public boolean isValidRating() {
        return rating >= 1 && rating <= 5;
    }

    public boolean isHighRating() {
        return rating >= 4;
    }

    public boolean isLowRating() {
        return rating <= 2;
    }

    public boolean isEscalated() {
        return isEscalated;
    }
}