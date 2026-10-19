package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;
import java.util.UUID;

public interface RatingRepository {
    UUID insertRating(Rating rating);

    ImmutableList<Rating> getAllRatings();

    /**
     * Attaches feedback to an existing rating.
     *
     * @return false if the rating doesn't exist or already has feedback
     */
    boolean saveFeedback(RatingFeedback feedback);

    ImmutableList<RatingFeedbackEntry> getAllFeedback();
}
