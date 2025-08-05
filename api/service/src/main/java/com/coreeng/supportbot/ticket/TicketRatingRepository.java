package com.coreeng.supportbot.ticket;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.UUID;

public interface TicketRatingRepository {
    
    UUID insertRating(TicketRating rating);
    
    @Nullable TicketRating findById(UUID ratingId);
    ImmutableList<TicketRating> findRatingsByStatus(String ticketStatus);
    ImmutableList<TicketRating> findRatingsByTag(String tagCode);
    ImmutableList<TicketRating> findEscalatedRatings();
}