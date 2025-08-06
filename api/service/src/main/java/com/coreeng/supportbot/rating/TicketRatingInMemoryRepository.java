package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Arrays;
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
                .id(id)
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
                .filter(rating -> ticketStatus.equals(rating.status()))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableList<TicketRating> findRatingsByTag(String tagCode) {
        return ratings.values().stream()
                .filter(rating -> rating.tags() != null && Arrays.asList(rating.tags()).contains(tagCode))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableList<TicketRating> findEscalatedRatings() {
        return ratings.values().stream()
                .filter(TicketRating::isEscalated)
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