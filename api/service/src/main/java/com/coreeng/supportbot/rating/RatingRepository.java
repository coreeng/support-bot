package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;

import java.util.UUID;

public interface RatingRepository {
    UUID insertRating(Rating rating);
    ImmutableList<Rating> findRatingsByStatus(String status);
}