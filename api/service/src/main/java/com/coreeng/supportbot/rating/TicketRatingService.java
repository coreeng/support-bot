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
public class TicketRatingService {
    private final TicketRatingRepository repository;

    @Transactional
    public UUID createRating(TicketRating rating) {
        return repository.insertRating(rating);
    }

    @Nullable
    public TicketRating findById(UUID id) {
        return repository.findById(id);
    }

    @Nullable
    public TicketRating findByAnonymousId(String anonymousId) {
        return repository.findByAnonymousId(anonymousId);
    }

    public boolean hasAlreadyRated(String anonymousId) {
        return findByAnonymousId(anonymousId) != null;
    }

    public ImmutableList<TicketRating> findRatingsByStatus(String status) {
        return repository.findRatingsByStatus(status);
    }

    public ImmutableList<TicketRating> findRatingsByTag(String tag) {
        return repository.findRatingsByTag(tag);
    }

    public ImmutableList<TicketRating> findEscalatedRatings() {
        return repository.findEscalatedRatings();
    }
}
