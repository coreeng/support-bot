package com.coreeng.supportbot.rating;

import java.util.UUID;

public interface RatingRepository {
    UUID insertRating(Rating rating);
}