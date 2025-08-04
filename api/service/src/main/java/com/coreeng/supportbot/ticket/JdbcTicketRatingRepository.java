package com.coreeng.supportbot.ratings;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.coreeng.supportbot.dbschema.Tables.TICKET_RATINGS;
import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class JdbcTicketRatingRepository implements TicketRatingRepository {
    private final DSLContext dsl;

    public UUID insertRating(TicketRating rating) {
        return dsl.insertInto(TICKET_RATINGS)
            .set(TICKET_RATINGS.RATING, rating.rating())
            .set(TICKET_RATINGS.RATING_SUBMITTED_TS, rating.ratingSubmittedTs())
            .set(TICKET_RATINGS.TICKET_STATUS_SNAPSHOT, 
                com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(rating.ticketStatusSnapshot()))
            .set(TICKET_RATINGS.TICKET_IMPACT_SNAPSHOT, rating.ticketImpactSnapshot())
            .set(TICKET_RATINGS.TAG_SNAPSHOT, rating.primaryTagSnapshot())
            .set(TICKET_RATINGS.ESCALATED, rating.escalated())
            .returningResult(TICKET_RATINGS.RATING_ID)
            .fetchOne(TICKET_RATINGS.RATING_ID);
    }

    @Nullable
    public TicketRating findById(UUID ratingId) {
        return dsl.select()
            .from(TICKET_RATINGS)
            .where(TICKET_RATINGS.RATING_ID.eq(ratingId))
            .fetchOne(this::mapToTicketRating);
    }


    public ImmutableList<TicketRating> findRatingsByStatus(String ticketStatus) {
        return fetchRatings(
            dsl.select()
                .from(TICKET_RATINGS)
                .where(TICKET_RATINGS.TICKET_STATUS_SNAPSHOT.eq(
                    com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(ticketStatus)
            ))
        );
    }

    public ImmutableList<TicketRating> findRatingsByTag(String tagCode) {
        return fetchRatings(
            dsl.select()
                .from(TICKET_RATINGS)
                .where(TICKET_RATINGS.TAG_SNAPSHOT.eq(tagCode))
        );
    }

    public ImmutableList<TicketRating> findEscalatedRatings() {
        return fetchRatings(
            dsl.select()
                .from(TICKET_RATINGS)
                .where(TICKET_RATINGS.ESCALATED.eq(true))
        );
    }

    private ImmutableList<TicketRating> fetchRatings(ResultQuery<?> query) {
        try (var stream = query.stream()) {
            return stream.map(this::mapToTicketRating)
                .collect(toImmutableList());
        }
    }

    private TicketRating mapToTicketRating(Record record) {
        return new TicketRating(
            record.getValue(TICKET_RATINGS.RATING_ID),
            record.getValue(TICKET_RATINGS.RATING),
            record.getValue(TICKET_RATINGS.RATING_SUBMITTED_TS),
            record.getValue(TICKET_RATINGS.RATING_SUBMITTED_TS_ISO),
            record.getValue(TICKET_RATINGS.TICKET_STATUS_SNAPSHOT).toString(),
            record.getValue(TICKET_RATINGS.TICKET_IMPACT_SNAPSHOT),
            record.getValue(TICKET_RATINGS.TAG_SNAPSHOT),
            record.getValue(TICKET_RATINGS.ESCALATED),
            record.getValue(TICKET_RATINGS.RATING_WEEK),
            record.getValue(TICKET_RATINGS.RATING_MONTH)
        );
    }
}