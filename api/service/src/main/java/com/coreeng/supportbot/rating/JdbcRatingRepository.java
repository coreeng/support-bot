package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.coreeng.supportbot.dbschema.Tables.RATINGS;
import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class JdbcRatingRepository implements RatingRepository {
    private final DSLContext dsl;

    @Override
    public UUID insertRating(Rating rating) {
        return dsl.insertInto(RATINGS)
                .set(RATINGS.RATING, rating.rating())
                .set(RATINGS.SUBMITTED_TS, rating.submittedTs())
                .set(RATINGS.STATUS, TicketStatus.lookupLiteral(rating.status().name()))
                .set(RATINGS.IMPACT, rating.impact())
                .set(RATINGS.TAGS, rating.tags())
                .set(RATINGS.IS_ESCALATED, rating.isEscalated())
                .returningResult(RATINGS.ID)
                .fetchOne(RATINGS.ID);
    }

    @Override
    public ImmutableList<Rating> findRatingsByStatus(String status) {
        return fetchRatings(
                dsl.select()
                        .from(RATINGS)
                        .where(RATINGS.STATUS.eq(
                                TicketStatus.lookupLiteral(status)
                        ))
        );
    }

    private ImmutableList<Rating> fetchRatings(ResultQuery<?> query) {
        try (var stream = query.stream()) {
            return stream.map(this::mapToRating)
                    .collect(toImmutableList());
        }
    }

    private Rating mapToRating(Record record) {
        return Rating.builder()
                .id(record.getValue(RATINGS.ID))
                .rating(record.getValue(RATINGS.RATING))
                .submittedTs(record.getValue(RATINGS.SUBMITTED_TS))
                .status(com.coreeng.supportbot.ticket.TicketStatus.valueOf(record.getValue(RATINGS.STATUS).toString()))
                .impact(record.getValue(RATINGS.IMPACT))
                .tags(record.getValue(RATINGS.TAGS))
                .isEscalated(record.getValue(RATINGS.IS_ESCALATED))
                .build();
    }
}
