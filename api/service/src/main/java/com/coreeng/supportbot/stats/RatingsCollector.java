package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class RatingsCollector implements StatsCollector<StatsRequest.Ratings> {
    private final RatingService ratingService;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketRating;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.Ratings request) {
        ImmutableList<Rating> allRatings = ratingService.getAllRatings();
        ImmutableList<Rating> filteredRatings;

        if (request.from() != null && request.to() != null) {
            long fromEpochSecond = request.from().atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            long toEpochSecond = request.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            filteredRatings = allRatings.stream()
                    .filter(rating -> {
                        long submittedEpoch = Long.parseLong(rating.submittedTs());
                        return submittedEpoch >= fromEpochSecond && submittedEpoch < toEpochSecond;
                    })
                    .collect(ImmutableList.toImmutableList());
        } else {
            filteredRatings = allRatings;
        }

        int count = filteredRatings.size();
        Double average = count > 0
                ? filteredRatings.stream()
                    .mapToInt(Rating::rating)
                    .average()
                    .orElse(0.0)
                : null;

        StatsResult.RatingsValues values = StatsResult.RatingsValues.builder()
                .average(average)
                .count(count)
                .build();

        return StatsResult.Ratings.builder()
                .request(request)
                .values(values)
                .build();
    }
}

