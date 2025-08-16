package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    @Nullable
    public Rating findByAnonymousId(String anonymousId) {
        return repository.findByAnonymousId(anonymousId);
    }

    public boolean hasAlreadyRated(String anonymousId) {
        return findByAnonymousId(anonymousId) != null;
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

    public String createAnonymousId(String ticketId, String userId) {
        String combined = ticketId + ":" + userId;
        return Hashing.sha256().hashString(combined, StandardCharsets.UTF_8).toString();
    }
}
