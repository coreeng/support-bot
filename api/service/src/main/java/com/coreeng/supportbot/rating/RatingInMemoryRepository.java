package com.coreeng.supportbot.rating;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
