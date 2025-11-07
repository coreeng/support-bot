package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rating.rest.RatingUI;
import com.coreeng.supportbot.rating.rest.RatingUIMapper;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class RatingsCollector implements StatsCollector<StatsRequest.Ratings> {
    private final RatingService ratingService;
    private final RatingUIMapper ratingUIMapper;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketRating;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.Ratings request) {
        ImmutableList<RatingUI> ratings = ratingService.getAllRatings()
                .stream()
                .map(ratingUIMapper::mapToUI)
                .collect(toImmutableList());
        
        return StatsResult.Ratings.builder()
                .request(request)
                .values(ratings)
                .build();
    }
}

