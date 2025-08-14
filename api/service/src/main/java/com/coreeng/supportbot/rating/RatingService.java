package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = ticketId + ":" + userId;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 is not available
            return String.valueOf((ticketId + ":" + userId).hashCode());
        }
    }
}
