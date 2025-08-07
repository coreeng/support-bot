package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Arrays;
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

    @Nullable
    @Override
    public Rating findById(UUID ratingId) {
        return ratings.get(ratingId);
    }

    @Nullable
    @Override
    public Rating findByAnonymousId(String anonymousId) {
        return ratings.values().stream()
                .filter(rating -> anonymousId.equals(rating.anonymousId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ImmutableList<Rating> findRatingsByStatus(String ticketStatus) {
        return ratings.values().stream()
                .filter(rating -> ticketStatus.equals(rating.status()))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableList<Rating> findRatingsByTag(String tagCode) {
        return ratings.values().stream()
                .filter(rating -> rating.tags() != null && Arrays.asList(rating.tags()).contains(tagCode))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableList<Rating> findEscalatedRatings() {
        return ratings.values().stream()
                .filter(Rating::isEscalated)
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