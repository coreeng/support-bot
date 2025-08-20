package com.coreeng.supportbot.rating;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.UUID;

public interface RatingRepository {
    
    UUID insertRating(Rating rating);
    
    @Nullable Rating findById(UUID id);
    ImmutableList<Rating> findRatingsByStatus(String status);
    ImmutableList<Rating> findRatingsByTag(String tag);
    ImmutableList<Rating> findEscalatedRatings();
}