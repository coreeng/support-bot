package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class TicketRatingInMemoryRepository implements TicketRatingRepository {
    private final Map<UUID, TicketRating> ratings = new HashMap<>();

    @Override
    public UUID insertRating(TicketRating rating) {
        UUID id = UUID.randomUUID();
        TicketRating savedRating = rating.toBuilder()
                .ratingId(id)
                .build();
        ratings.put(id, savedRating);
        return id;
    }

    @Nullable
    @Override
    public TicketRating findById(UUID ratingId) {
        return ratings.get(ratingId);
    }

    @Override
    public ImmutableList<TicketRating> findRatingsByStatus(String ticketStatus) {
        return ratings.values().stream()
                .filter(rating -> ticketStatus.equals(rating.ticketStatusSnapshot()))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableList<TicketRating> findRatingsByTag(String tagCode) {
        return ratings.values().stream()
                .filter(rating -> tagCode.equals(rating.primaryTagSnapshot()))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableList<TicketRating> findEscalatedRatings() {
        return ratings.values().stream()
                .filter(TicketRating::escalated)
                .collect(toImmutableList());
    }

    // Helper methods for testing
    public void clear() {
        ratings.clear();
    }

    public int size() {
        return ratings.size();
    }
}