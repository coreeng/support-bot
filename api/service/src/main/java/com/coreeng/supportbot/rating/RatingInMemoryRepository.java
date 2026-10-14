package com.coreeng.supportbot.rating;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class RatingInMemoryRepository implements RatingRepository {
    private final Map<UUID, Rating> ratings = new HashMap<>();
    private final Map<UUID, RatingFeedback> feedback = new HashMap<>();

    @Override
    public UUID insertRating(Rating rating) {
        UUID id = UUID.randomUUID();
        Rating savedRating = rating.toBuilder().id(id).build();
        ratings.put(id, savedRating);
        return id;
    }

    @Override
    public ImmutableList<Rating> getAllRatings() {
        return ratings.values().stream().collect(toImmutableList());
    }

    @Override
    public boolean saveFeedback(RatingFeedback ratingFeedback) {
        if (!ratings.containsKey(ratingFeedback.ratingId()) || feedback.containsKey(ratingFeedback.ratingId())) {
            return false;
        }
        feedback.put(ratingFeedback.ratingId(), ratingFeedback);
        return true;
    }

    @Override
    public ImmutableList<RatingFeedbackEntry> getAllFeedback() {
        return feedback.values().stream()
                .map(f -> {
                    Rating rating = checkNotNull(ratings.get(f.ratingId()));
                    return new RatingFeedbackEntry(
                            f.ratingId(), rating.rating(), rating.submittedTs(), f.feedback(), f.ticketId());
                })
                .collect(toImmutableList());
    }

    @Nullable public Rating findById(UUID ratingId) {
        return ratings.get(ratingId);
    }

    @Nullable public RatingFeedback findFeedbackById(UUID ratingId) {
        return feedback.get(ratingId);
    }

    // Helper methods for testing
    public void clear() {
        ratings.clear();
        feedback.clear();
    }

    public int size() {
        return ratings.size();
    }
}
