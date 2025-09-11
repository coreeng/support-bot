package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RatingService {
    private final RatingRepository repository;

    @Transactional
    public UUID createRating(Rating rating) {
        return repository.insertRating(rating);
    }

    @Nullable
    public Rating findById(UUID id) {
        return repository.findById(id);
    }

    public ImmutableList<Rating> findRatingsByStatus(String status) {
        return repository.findRatingsByStatus(status);
    }

    public ImmutableList<Rating> findRatingsByTag(String tag) {
        return repository.findRatingsByTag(tag);
    }

    public ImmutableList<Rating> findEscalatedRatings() {
        return repository.findEscalatedRatings();
    }
}
