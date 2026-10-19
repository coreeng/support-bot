package com.coreeng.supportbot.rating;

import static com.coreeng.supportbot.dbschema.Tables.RATINGS;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcRatingRepository implements RatingRepository {
    private final DSLContext dsl;

    @Override
    public UUID insertRating(Rating rating) {
        return checkNotNull(dsl.insertInto(RATINGS)
                .set(RATINGS.RATING, rating.rating())
                .set(RATINGS.SUBMITTED_TS, rating.submittedTs())
                .set(RATINGS.STATUS, TicketStatus.lookupLiteral(rating.status().name()))
                .set(RATINGS.IMPACT, rating.impact())
                .set(RATINGS.TAGS, rating.tags())
                .set(RATINGS.IS_ESCALATED, rating.isEscalated())
                .returningResult(RATINGS.ID)
                .fetchOne(RATINGS.ID));
    }

    @Override
    public ImmutableList<Rating> getAllRatings() {
        return fetchRatings(dsl.select().from(RATINGS));
    }

    @Override
    public boolean saveFeedback(RatingFeedback feedback) {
        TicketId ticketId = feedback.ticketId();
        return dsl.update(RATINGS)
                        .set(RATINGS.FEEDBACK, feedback.feedback())
                        .set(RATINGS.TICKET_ID, ticketId != null ? ticketId.id() : null)
                        .where(RATINGS.ID.eq(feedback.ratingId()).and(RATINGS.FEEDBACK.isNull()))
                        .execute()
                > 0;
    }

    @Override
    public ImmutableList<RatingFeedbackEntry> getAllFeedback() {
        try (var stream = dsl.selectFrom(RATINGS).where(RATINGS.FEEDBACK.isNotNull()).stream()) {
            return stream.map(r -> {
                        Long ticketId = r.getTicketId();
                        return new RatingFeedbackEntry(
                                checkNotNull(r.getId()),
                                checkNotNull(r.getRating()),
                                checkNotNull(r.getSubmittedTs()),
                                checkNotNull(r.getFeedback()),
                                ticketId != null ? new TicketId(ticketId) : null);
                    })
                    .collect(toImmutableList());
        }
    }

    private ImmutableList<Rating> fetchRatings(ResultQuery<?> query) {
        try (var stream = query.stream()) {
            return stream.map(this::mapToRating).collect(toImmutableList());
        }
    }

    private Rating mapToRating(Record record) {
        return Rating.builder()
                .id(record.getValue(RATINGS.ID))
                .rating(record.getValue(RATINGS.RATING))
                .submittedTs(record.getValue(RATINGS.SUBMITTED_TS))
                .status(com.coreeng.supportbot.ticket.TicketStatus.valueOf(
                        record.getValue(RATINGS.STATUS).toString()))
                .impact(record.getValue(RATINGS.IMPACT))
                .tags(record.getValue(RATINGS.TAGS))
                .isEscalated(record.getValue(RATINGS.IS_ESCALATED))
                .build();
    }
}
