package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.coreeng.supportbot.dbschema.Tables.RATINGS;

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
}