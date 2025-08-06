package com.coreeng.supportbot.rating;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.UUID;

import static com.coreeng.supportbot.dbschema.Tables.TICKET_RATINGS;
import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class JdbcTicketRatingRepository implements TicketRatingRepository {
    private final DSLContext dsl;

    @Override
    public UUID insertRating(TicketRating rating) {
        return dsl.insertInto(TICKET_RATINGS)
            .set(TICKET_RATINGS.RATING, rating.rating())
            .set(TICKET_RATINGS.SUBMITTED_TS, rating.submittedTs())
            .set(TICKET_RATINGS.STATUS, 
                com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(rating.status()))
            .set(TICKET_RATINGS.IMPACT, rating.impact())
            .set(TICKET_RATINGS.TAGS, rating.tags())
            .set(TICKET_RATINGS.ESCALATED_TEAMS, rating.escalatedTeams())
            .returningResult(TICKET_RATINGS.ID)
            .fetchOne(TICKET_RATINGS.ID);
    }

    @Override
    @Nullable
    public TicketRating findById(UUID id) {
        return dsl.select()
            .from(TICKET_RATINGS)
            .where(TICKET_RATINGS.ID.eq(id))
            .fetchOne(this::mapToTicketRating);
    }

    @Override
    public ImmutableList<TicketRating> findRatingsByStatus(String status) {
        return fetchRatings(
            dsl.select()
                .from(TICKET_RATINGS)
                .where(TICKET_RATINGS.STATUS.eq(
                    com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(status)
            ))
        );
    }

    @Override
    public ImmutableList<TicketRating> findRatingsByTag(String tag) {
        return fetchRatings(
            dsl.select()
                .from(TICKET_RATINGS)
                .where(TICKET_RATINGS.TAGS.contains(new String[]{tag}))
        );
    }

    @Override
    public ImmutableList<TicketRating> findEscalatedRatings() {
        return fetchRatings(
            dsl.select()
                .from(TICKET_RATINGS)
                .where(TICKET_RATINGS.ESCALATED_TEAMS.isNotNull()
                    .and(TICKET_RATINGS.ESCALATED_TEAMS.notEqual(new String[]{})))
        );
    }

    private ImmutableList<TicketRating> fetchRatings(ResultQuery<?> query) {
        try (var stream = query.stream()) {
            return stream.map(this::mapToTicketRating)
                .collect(toImmutableList());
        }
    }

    private TicketRating mapToTicketRating(Record record) {
        return TicketRating.builder()
            .id(record.getValue(TICKET_RATINGS.ID))
            .rating(record.getValue(TICKET_RATINGS.RATING))
            .submittedTs(record.getValue(TICKET_RATINGS.SUBMITTED_TS))
            .status(record.getValue(TICKET_RATINGS.STATUS).toString())
            .impact(record.getValue(TICKET_RATINGS.IMPACT))
            .tags(record.getValue(TICKET_RATINGS.TAGS))
            .escalatedTeams(record.getValue(TICKET_RATINGS.ESCALATED_TEAMS))
            .build();
    }
}