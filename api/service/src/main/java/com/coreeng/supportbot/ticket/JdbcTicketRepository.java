package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.dbschema.enums.EscalationStatus;
import com.coreeng.supportbot.dbschema.enums.TicketEventType;
import com.coreeng.supportbot.dbschema.tables.records.TicketLogRecord;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.util.Page;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectLimitPercentAfterOffsetStep;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static com.coreeng.supportbot.dbschema.Tables.*;
import static com.coreeng.supportbot.util.JooqUtils.bigCount;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static org.jooq.impl.DSL.*;
import static org.jooq.impl.SQLDataType.CLOB;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
public class JdbcTicketRepository implements TicketRepository {
    private final DSLContext dsl;
    private final ZoneId timezone;

    @Override
    public void createQueryIfNotExists(MessageRef queryRef) {
        getQueryIdOrCreate(queryRef);
    }

    private long getQueryIdOrCreate(MessageRef queryRef) {
        Long id = dsl.insertInto(QUERY, QUERY.TS, QUERY.CHANNEL_ID, QUERY.DATE)
            .values(queryRef.ts().ts(), queryRef.channelId(), queryRef.ts().getDate())
            .onConflict(QUERY.TS, QUERY.CHANNEL_ID).doUpdate().setAllToExcluded()
            .returning(QUERY.ID)
            .fetchSingle(QUERY.ID);
        return checkNotNull(id);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean queryExists(MessageRef queryRef) {
        return dsl.fetchExists(
            QUERY,
            QUERY.TS.eq(queryRef.ts().ts())
                .and(QUERY.CHANNEL_ID.eq(queryRef.channelId()))
        );
    }

    @Override
    public Ticket createTicketIfNotExists(Ticket ticket) {
        checkNotNull(ticket);
        checkArgument(ticket.id() == null);

        long queryId = getQueryIdOrCreate(ticket.queryRef());
        Long ticketId = dsl.insertInto(
                TICKET,
                TICKET.QUERY_ID,
                TICKET.CREATED_MESSAGE_TS,
                TICKET.STATUS,
                TICKET.TEAM,
                TICKET.IMPACT_CODE,
                TICKET.LAST_INTERACTED_AT
            )
            .values(
                queryId,
                ticket.createdMessageTs() != null
                    ? ticket.createdMessageTs().ts()
                    : null,
                com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(ticket.status().name()),
                ticket.team(),
                ticket.impact(),
                ticket.lastInteractedAt()
            )
            .onConflictDoNothing()
            .returning(TICKET.ID)
            .fetchOne(TICKET.ID);

        if (ticketId == null) {
            return findTicketByQuery(ticket.queryRef());
        }

        Ticket updatedTicket = ticket.withId(new TicketId(ticketId));

        updateTicketTags(updatedTicket);

        dsl.batchInsert(
            updatedTicket.statusLog().stream()
                .map(l -> new TicketLogRecord()
                    .with(TICKET_LOG.TICKET_ID, checkNotNull(updatedTicket.id()).id())
                    .with(TICKET_LOG.EVENT, TicketEventType.lookupLiteral(l.status().name()))
                    .with(TICKET_LOG.DATE, l.date())
                )
                .toList()
        ).execute();

        return updatedTicket;
    }

    @Override
    public Ticket updateTicket(Ticket ticket) {
        checkNotNull(ticket);
        checkArgument(ticket.id() != null);

        int updatedTickets = dsl.update(TICKET)
            .set(TICKET.CREATED_MESSAGE_TS, ticket.createdMessageTs() != null ? ticket.createdMessageTs().ts() : null)
            .set(TICKET.STATUS, com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(ticket.status().name()))
            .set(TICKET.TEAM, ticket.team())
            .set(TICKET.IMPACT_CODE, ticket.impact())
            .set(TICKET.LAST_INTERACTED_AT, ticket.lastInteractedAt())
            .where(TICKET.ID.eq(ticket.id().id()))
            .execute();
        if (updatedTickets == 0) {
            log.atWarn()
                .addArgument(ticket::id)
                .log("No updated tickets found for ticket id {}");
            return ticket;
        }

        updateTicketTags(ticket);

        return ticket;
    }

    @Override
    public boolean touchTicketById(TicketId id, Instant timestamp) {
        checkNotNull(id);
        checkNotNull(timestamp);
        return dsl.update(TICKET)
            .set(TICKET.LAST_INTERACTED_AT, timestamp)
            .where(TICKET.ID.eq(id.id()))
            .execute() > 0;
    }

    private void updateTicketTags(Ticket ticket) {
        dsl.deleteFrom(TICKET_TO_TAG)
            .where(TICKET_TO_TAG.TICKET_ID.eq(checkNotNull(ticket.id()).id()).and(
                TICKET_TO_TAG.TAG_CODE.notEqual(any(ticket.tags().toArray(String[]::new)))
            ))
            .execute();

        dsl.insertInto(TICKET_TO_TAG, TICKET_TO_TAG.TICKET_ID, TICKET_TO_TAG.TAG_CODE)
            .valuesOfRows(
                ticket.tags().stream()
                    .map(t -> row(ticket.id().id(), t))
                    .toList()
            )
            .onConflict(TICKET_TO_TAG.TICKET_ID, TICKET_TO_TAG.TAG_CODE).doNothing()
            .execute();
    }

    @Transactional(readOnly = true)
    @Nullable
    @Override
    public Ticket findTicketById(TicketId ticketId) {
        var query = dsl.select(selectTicketFields())
            .from(TICKET)
            .join(QUERY).on(TICKET.QUERY_ID.eq(QUERY.ID))
            .where(TICKET.ID.eq(ticketId.id()));
        return query.fetchOptional(this::buildTicketFromRow)
            .map(this::populateTicket)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    @Nullable
    @Override
    public Ticket findTicketByQuery(MessageRef queryRef) {
        var query = dsl.select(selectTicketFields())
            .from(TICKET)
            .join(QUERY).on(TICKET.QUERY_ID.eq(QUERY.ID))
            .where(QUERY.TS.eq(queryRef.ts().ts()).and(QUERY.CHANNEL_ID.eq(queryRef.channelId())));
        return query.fetchOptional(this::buildTicketFromRow)
            .map(this::populateTicket)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    @Nullable
    @Override
    public DetailedTicket findDetailedById(TicketId id) {
        String escalatedField = "escalated";
        var query = dsl.select(
                ImmutableList.<SelectField<?>>builder()
                    .addAll(selectTicketFields())
                    .add(createIsEscalatedQuery().as(escalatedField))
                    .build()
            )
            .from(TICKET)
            .join(QUERY).on(TICKET.QUERY_ID.eq(QUERY.ID))
            .where(TICKET.ID.eq(id.id()));
        return query.fetchOptional(r -> new DetailedTicket(
                buildTicketFromRow(r),
                r.get(escalatedField, Boolean.class)
            ))
            .map(t -> new DetailedTicket(
                populateTicket(t.ticket()),
                t.escalated()
            ))
            .orElse(null);
    }

    @Override
    public Ticket insertStatusLog(Ticket ticket, Instant at) {
        Ticket.StatusLog log = dsl.insertInto(TICKET_LOG, TICKET_LOG.TICKET_ID, TICKET_LOG.EVENT, TICKET_LOG.DATE)
            .values(
                checkNotNull(ticket.id()).id(),
                TicketEventType.lookupLiteral(ticket.status().name()),
                at
            )
            .returning(TICKET_LOG.EVENT, TICKET_LOG.DATE)
            .fetchSingle(r -> new Ticket.StatusLog(
                TicketStatus.valueOf(r.getEvent().getLiteral()),
                r.getDate()
            ));
        return ticket.toBuilder()
            .statusLog(ImmutableList.<Ticket.StatusLog>builder()
                .addAll(ticket.statusLog())
                .add(log)
                .build())
            .build();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Ticket> listTickets(TicketsQuery query) {
        checkNotNull(query);
        checkArgument(query.page() >= 0);
        checkArgument(query.pageSize() > 0);

        List<Ticket> tickets = createFindQuery(query, selectTicketFields())
            .fetch(this::buildTicketFromRow);
        ImmutableList<Long> ids = tickets.stream()
            .map(t -> checkNotNull(t.id()).id())
            .collect(toImmutableList());
        ImmutableListMultimap<TicketId, Ticket.StatusLog> logsByTicketId = fetchStatusLogs(ids);
        ImmutableListMultimap<TicketId, String> tagsByTicketId = fetchTags(ids);
        ImmutableList<Ticket> content = tickets.stream()
            .map(t -> t.toBuilder()
                .statusLog(logsByTicketId.get(checkNotNull(t.id())))
                .tags(tagsByTicketId.get(t.id()))
                .build())
            .collect(toImmutableList());

        long ticketsTotal;
        if (query.unlimited()) {
            ticketsTotal = tickets.size();
        } else {
            ticketsTotal = checkNotNull(
                createFindQuery(
                    query.toBuilder()
                        .order(null)
                        .unlimited(true)
                        .build(),
                    ImmutableList.of(bigCount())
                ).fetchOne(0, Long.class)
            );
        }
        return new Page<>(
            content,
            query.page(),
            (int) Math.ceil((double) ticketsTotal / query.pageSize()), // Convert to double before dividing, then use Math.ceil to round up
            ticketsTotal
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Page<DetailedTicket> listDetailedTickets(TicketsQuery query) {
        checkNotNull(query);
        checkArgument(query.page() >= 0);
        checkArgument(query.pageSize() > 0);

        String escalatedField = "escalated";
        List<DetailedTicket> tickets = createFindQuery(query, ImmutableList.<SelectField<?>>builder()
            .addAll(selectTicketFields())
            .add(createIsEscalatedQuery().as(escalatedField))
            .build()
        ).fetch(r -> new DetailedTicket(
            buildTicketFromRow(r),
            r.get(escalatedField, Boolean.class)
        ));
        ImmutableList<Long> ids = tickets.stream()
            .map(t -> checkNotNull(t.ticket().id()).id())
            .collect(toImmutableList());
        ImmutableListMultimap<TicketId, Ticket.StatusLog> logsByTicketId = fetchStatusLogs(ids);
        ImmutableListMultimap<TicketId, String> tagsByTicketId = fetchTags(ids);
        ImmutableList<DetailedTicket> content = tickets.stream()
            .map(t -> new DetailedTicket(
                t.ticket().toBuilder()
                    .statusLog(logsByTicketId.get(checkNotNull(t.ticket().id())))
                    .tags(tagsByTicketId.get(t.ticket().id()))
                    .build(),
                t.escalated()
            ))
            .collect(toImmutableList());

        long ticketsTotal;
        if (query.unlimited()) {
            ticketsTotal = tickets.size();
        } else {
            ticketsTotal = checkNotNull(
                createFindQuery(
                    query.toBuilder()
                        .order(null)
                        .unlimited(true)
                        .build(),
                    ImmutableList.of(bigCount())
                ).fetchOne(0, Long.class)
            );
        }
        return new Page<>(
            content,
            query.page(),
            ticketsTotal / query.pageSize() + 1,
            ticketsTotal
        );
    }

    @Override
    public ImmutableList<TicketId> listStaleTicketIds(Instant checkAt, Duration timeToStale) {
        Instant stalenessThreshold = checkAt.minus(timeToStale);
        try (var stream = dsl.select(TICKET.ID)
            .from(TICKET)
            .where(TICKET.STATUS.eq(com.coreeng.supportbot.dbschema.enums.TicketStatus.opened).and(
                TICKET.LAST_INTERACTED_AT.lt(stalenessThreshold))
            )
            .stream()) {
            return stream.map(r -> new TicketId(r.get(TICKET.ID)))
                .collect(toImmutableList());
        }
    }

    @Override
    public ImmutableList<TicketId> listStaleTicketIdsToRemindOf(Instant checkAt, Duration reminderInterval) {
        Instant reminderThreshold = checkAt.minus(reminderInterval);
        try (var stream = dsl.select(TICKET.ID)
            .from(TICKET)
            .where(TICKET.STATUS.eq(com.coreeng.supportbot.dbschema.enums.TicketStatus.stale).and(
                TICKET.LAST_INTERACTED_AT.lt(reminderThreshold))
            )
            .stream()) {
            return stream.map(r -> new TicketId(r.get(TICKET.ID)))
                .collect(toImmutableList());
        }
    }

    private SelectLimitPercentAfterOffsetStep<Record> createFindQuery(
        TicketsQuery query,
        List<SelectField<?>> selectFields
    ) {
        CommonTableExpression<?> taggedTicketsCTE = query.tags().isEmpty()
            ? null
            : name("taggedTickets").fields("id", "tags")
            .as(selectDistinct(TICKET.ID, arrayAgg(TICKET_TO_TAG.TAG_CODE))
                .from(TICKET)
                .join(TICKET_TO_TAG).on(
                    TICKET.ID.eq(TICKET_TO_TAG.TICKET_ID)
                        .and(TICKET_TO_TAG.TAG_CODE.eq(any(
                            query.tags().toArray(String[]::new)
                        )))
                )
                .groupBy(TICKET.ID));
        return dsl
            .with(taggedTicketsCTE)
            .select(selectFields)
            .from(TICKET)
            .join(QUERY).on(TICKET.QUERY_ID.eq(QUERY.ID))
            .where(ticketsQueryToCondition(query, taggedTicketsCTE))
            .orderBy(switch (query.order()) {
                case asc -> QUERY.DATE.asc();
                case desc -> QUERY.DATE.desc();
                case null -> noField();
            })
            .limit(
                query.unlimited()
                    ? noField(Long.class)
                    : value(query.page() * query.pageSize()),
                query.unlimited()
                    ? noField(Long.class)
                    : value(query.pageSize())
            );
    }

    private Condition ticketsQueryToCondition(
        TicketsQuery query,
        @Nullable CommonTableExpression<?> taggedTicketsCTE
    ) {
        Condition condition = noCondition();
        if (!query.ids().isEmpty()) {
            Long[] ids = query.ids().stream()
                .map(TicketId::id)
                .toArray(Long[]::new);
            condition = condition.and(TICKET.ID.eq(any(ids)));
        }
        if (query.status() != null) {
            condition = condition.and(TICKET.STATUS.eq(
                com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(query.status().name()))
            );
        }
        if (query.dateFrom() != null) {
            Instant dateFrom = query.dateFrom().atStartOfDay(timezone).toInstant();
            condition = condition.and(QUERY.DATE.ge(dateFrom));
        }
        if (query.dateTo() != null) {
            Instant dateTo = query.dateTo().plusDays(1).atStartOfDay(timezone).toInstant();
            condition = condition.and(QUERY.DATE.le(dateTo));
        }
        if (query.escalated() != null) {
            condition = condition.and(createIsEscalatedQuery().eq(query.escalated()));
        }
        if (!query.impacts().isEmpty()) {
            condition = condition.and(
                TICKET.IMPACT_CODE.isNotNull()
                    .and(TICKET.IMPACT_CODE.eq(any(
                        query.impacts().toArray(String[]::new)
                    )))
            );
        }
        if (!query.teams().isEmpty()) {
            condition = condition.and(
                TICKET.TEAM.isNotNull()
                    .and(TICKET.TEAM.eq(any(
                        query.teams().toArray(String[]::new)
                    )))
            );
        }
        if (!query.tags().isEmpty()) {
            checkNotNull(taggedTicketsCTE);
            var taggedTicketIds = checkNotNull(taggedTicketsCTE.field("id", Long.class));
            var taggedTicketTags = checkNotNull(taggedTicketsCTE.field("tags", CLOB.array()));
            condition = condition.and(exists(
                selectOne()
                    .from(taggedTicketsCTE)
                    .where(TICKET.ID.eq(taggedTicketIds)
                        .and(taggedTicketTags.contains(
                            cast(query.tags().toArray(String[]::new), CLOB.array())
                        )))
            ));
        }
        if (!Strings.isNullOrEmpty(query.escalationTeam())) {
            condition = condition.and(exists(
                selectOne()
                    .from(ESCALATION)
                    .where(ESCALATION.TICKET_ID.eq(TICKET.ID)
                        .and(ESCALATION.TEAM.eq(query.escalationTeam()))
            )));
        }
        return condition;
    }

    private Ticket populateTicket(Ticket t) {
        TicketId id = checkNotNull(t.id());
        ImmutableList<Ticket.StatusLog> logs = fetchStatusLogs(ImmutableList.of(id.id()))
            .get(id);
        ImmutableList<String> tags = fetchTags(ImmutableList.of(id.id()))
            .get(id);

        return t.toBuilder()
            .statusLog(logs)
            .tags(tags)
            .build();
    }

    private ImmutableListMultimap<TicketId, Ticket.StatusLog> fetchStatusLogs(ImmutableCollection<Long> ids) {
        try (var stream = dsl.select(TICKET_LOG.TICKET_ID, TICKET_LOG.EVENT, TICKET_LOG.DATE)
            .from(TICKET_LOG)
            .where(TICKET_LOG.TICKET_ID.eq(any(ids.toArray(Long[]::new))))
            .orderBy(TICKET_LOG.TICKET_ID, TICKET_LOG.DATE)
            .stream()) {
            return stream.collect(toImmutableListMultimap(
                r -> new TicketId(r.get(TICKET_LOG.TICKET_ID)),
                r -> new Ticket.StatusLog(
                    TicketStatus.valueOf(r.get(TICKET_LOG.EVENT).getLiteral()),
                    r.get(TICKET_LOG.DATE)
                )
            ));
        }
    }

    private ImmutableListMultimap<TicketId, String> fetchTags(ImmutableCollection<Long> ids) {
        try (var stream = dsl.select(TICKET_TO_TAG.TICKET_ID, TICKET_TO_TAG.TAG_CODE)
            .from(TICKET_TO_TAG)
            .where(TICKET_TO_TAG.TICKET_ID.eq(any(ids.toArray(Long[]::new))))
            .stream()) {
            return stream.collect(toImmutableListMultimap(
                r -> new TicketId(r.get(TICKET_TO_TAG.TICKET_ID)),
                r -> r.get(TICKET_TO_TAG.TAG_CODE)
            ));
        }
    }

    private Condition createIsEscalatedQuery() {
        return exists(
            dsl.selectOne()
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(TICKET.ID)
                    .and(ESCALATION.STATUS.eq(EscalationStatus.opened)))
        );
    }

    private ImmutableList<SelectField<?>> selectTicketFields() {
        return ImmutableList.of(
            TICKET.ID,
            QUERY.TS,
            QUERY.CHANNEL_ID,
            TICKET.CREATED_MESSAGE_TS,
            TICKET.STATUS,
            TICKET.TEAM,
            TICKET.IMPACT_CODE,
            TICKET.RATING_SUBMITTED
        );
    }

    private Ticket buildTicketFromRow(Record r) {
        return Ticket.builder()
            .id(new TicketId(r.get(TICKET.ID)))
            .queryTs(MessageTs.of(r.get(QUERY.TS)))
            .channelId(r.get(QUERY.CHANNEL_ID))
            .createdMessageTs(MessageTs.ofOrNull(r.get(TICKET.CREATED_MESSAGE_TS)))
            .status(TicketStatus.valueOf(r.get(TICKET.STATUS).getLiteral()))
            .team(r.get(TICKET.TEAM))
            .impact(r.get(TICKET.IMPACT_CODE))
            .ratingSubmitted(r.get(TICKET.RATING_SUBMITTED))
            .build();
    }

    @Override
    public boolean isTicketRated(TicketId ticketId) {
        return dsl
            .select(TICKET.RATING_SUBMITTED)
            .from(TICKET)
            .where(TICKET.ID.eq(ticketId.id()))
            .fetchOptional()
            .map(r -> r.get(TICKET.RATING_SUBMITTED))
            .orElse(false);
    }

    @Override
    public void markTicketAsRated(TicketId ticketId) {
        dsl
            .update(TICKET)
            .set(TICKET.RATING_SUBMITTED, true)
            .where(TICKET.ID.eq(ticketId.id()))
            .execute();
    }
}

