package com.coreeng.supportbot.rating;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.UUID;

public interface TicketRatingRepository {
    
    UUID insertRating(TicketRating rating);
    
    @Nullable TicketRating findById(UUID id);
    @Nullable TicketRating findByAnonymousId(String anonymousId);
    ImmutableList<TicketRating> findRatingsByStatus(String status);
    ImmutableList<TicketRating> findRatingsByTag(String tag);
    ImmutableList<TicketRating> findEscalatedRatings();
}