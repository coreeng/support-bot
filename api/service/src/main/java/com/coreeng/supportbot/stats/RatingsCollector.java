package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        ImmutableList<Rating> filtered = filterByRange(allRatings, request);

        int count = filtered.size();
        Double average =
                count > 0 ? filtered.stream().mapToInt(Rating::rating).average().orElse(0.0) : null;

        // group by week start (Monday)
        Map<LocalDate, List<Rating>> byWeek =
                filtered.stream().collect(Collectors.groupingBy(r -> weekStartOf(r.submittedTs())));

        ImmutableList<StatsResult.WeeklyRating> weekly = byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<Rating> rs = e.getValue();
                    double avg = rs.stream().mapToInt(Rating::rating).average().orElse(0.0);
                    return StatsResult.WeeklyRating.builder()
                            .weekStart(e.getKey())
                            .average(avg)
                            .count(rs.size())
                            .build();
                })
                .collect(ImmutableList.toImmutableList());

        StatsResult.RatingsValues values = StatsResult.RatingsValues.builder()
                .average(average)
                .count(count)
                .build();

        return StatsResult.Ratings.builder()
                .request(request)
                .values(values)
                .weekly(weekly)
                .build();
    }

    private ImmutableList<Rating> filterByRange(ImmutableList<Rating> ratings, StatsRequest.Ratings req) {
        if (req.from() == null || req.to() == null) {
            return ratings;
        }
        long fromEpoch = req.from().atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long toEpoch = req.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        return ratings.stream()
                .filter(r -> {
                    long submittedEpoch = Long.parseLong(r.submittedTs());
                    return submittedEpoch >= fromEpoch && submittedEpoch < toEpoch;
                })
                .collect(ImmutableList.toImmutableList());
    }

    private LocalDate weekStartOf(String submittedTs) {
        long epoch = Long.parseLong(submittedTs);
        LocalDate date = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate();
        return date.with(java.time.DayOfWeek.MONDAY);
    }
}
