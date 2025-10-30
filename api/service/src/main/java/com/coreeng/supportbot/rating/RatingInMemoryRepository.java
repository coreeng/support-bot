package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class RatingInMemoryRepository implements RatingRepository {
    private final Map<UUID, Rating> ratings = new HashMap<>();

    @Override
    public UUID insertRating(Rating rating) {
        UUID id = UUID.randomUUID();
        Rating savedRating = rating.toBuilder()
                .id(id)
                .build();
        ratings.put(id, savedRating);
        return id;
    }

    @Override
    public ImmutableList<Rating> findRatingsByStatus(String ticketStatus) {
        return ratings.values().stream()
                .filter(rating -> TicketStatus.valueOf(ticketStatus).equals(rating.status()))
                .collect(toImmutableList());
    }

    public Rating findById(UUID ratingId) {
        return ratings.get(ratingId);
    }

    // Helper methods for testing
    public void clear() {
        ratings.clear();
    }

    public int size() {
        return ratings.size();
    }
}
