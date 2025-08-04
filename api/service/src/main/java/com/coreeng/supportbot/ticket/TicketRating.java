package com.coreeng.supportbot.ratings;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class TicketRating {
    @With
    @Nullable
    private UUID ratingId;
    private int rating;
    private String ratingSubmittedTs;
    @Nullable
    private Instant ratingSubmittedTsIso;
    private String ticketStatusSnapshot;
    @Nullable
    private String ticketImpactSnapshot;
    @Nullable
    private String primaryTagSnapshot;
    @Builder.Default
    private boolean escalated = false;
    @Nullable
    private LocalDate ratingWeek;
    @Nullable
    private LocalDate ratingMonth;

    /**
     * Create a new rating for submission (before database insert)
     */
    public static TicketRating createNew(
        int rating,
        String ratingSubmittedTs,
        String ticketStatusSnapshot,
        @Nullable String ticketImpactSnapshot,
        @Nullable String primaryTagSnapshot,
        boolean escalated
    ) {
        return TicketRating.builder()
            .rating(rating)
            .ratingSubmittedTs(ratingSubmittedTs)
            .ticketStatusSnapshot(ticketStatusSnapshot)
            .ticketImpactSnapshot(ticketImpactSnapshot)
            .primaryTagSnapshot(primaryTagSnapshot)
            .escalated(escalated)
            .build();
    }

    /**
     * Validate that the rating is within acceptable range
     */
    public boolean isValidRating() {
        return rating >= 1 && rating <= 5;
    }

    /**
     * Check if this is a high rating (4 or 5 stars)
     */
    public boolean isHighRating() {
        return rating >= 4;
    }

    /**
     * Check if this is a low rating (1 or 2 stars)
     */
    public boolean isLowRating() {
        return rating <= 2;
    }
}