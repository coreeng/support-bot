package com.coreeng.supportbot.ticket;

import static com.coreeng.supportbot.dbschema.Tables.ESCALATION;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static com.coreeng.supportbot.dbschema.Tables.TICKET_LOG;
import static com.coreeng.supportbot.dbschema.Tables.TICKET_TO_TAG;
import static com.coreeng.supportbot.util.JooqUtils.bigCount;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static org.jooq.impl.DSL.any;
import static org.jooq.impl.DSL.arrayAgg;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.noField;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.selectDistinct;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.value;
import static org.jooq.impl.SQLDataType.CLOB;

import com.coreeng.supportbot.dbschema.enums.EscalationStatus;
import com.coreeng.supportbot.dbschema.enums.TicketEventType;
import com.coreeng.supportbot.dbschema.tables.records.TicketLogRecord;
import com.coreeng.supportbot.dbschema.tables.records.TicketRecord;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.util.Page;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
public class JdbcTicketRepository implements TicketRepository {
    private final DSLContext dsl;
    private final AssigneeCrypto assigneeCrypto;

    @Override
    public void createQueryIfNotExists(MessageRef queryRef) {
        getQueryIdOrCreate(queryRef);
    }

    private long getQueryIdOrCreate(MessageRef queryRef) {
        Long id = dsl.insertInto(QUERY, QUERY.TS, QUERY.CHANNEL_ID, QUERY.DATE)
                .values(queryRef.ts().ts(), queryRef.channelId(), queryRef.ts().getDate())
                .onConflict(QUERY.TS, QUERY.CHANNEL_ID)
                .doUpdate()
                .setAllToExcluded()
                .returning(QUERY.ID)
                .fetchSingle(QUERY.ID);
        return checkNotNull(id);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean queryExists(MessageRef queryRef) {
        return dsl.fetchExists(QUERY, QUERY.TS.eq(queryRef.ts().ts()).and(QUERY.CHANNEL_ID.eq(queryRef.channelId())));
    }

    @Override
    public boolean deleteQueryIfNoTicket(MessageRef queryRef) {
        int deleted = dsl.deleteFrom(QUERY)
                .where(QUERY.TS
                        .eq(queryRef.ts().ts())
                        .and(QUERY.CHANNEL_ID.eq(queryRef.channelId()))
                        .andNotExists(dsl.selectOne().from(TICKET).where(TICKET.QUERY_ID.eq(QUERY.ID))))
                .execute();
        return deleted > 0;
    }

    @Override
    public Ticket createTicketIfNotExists(Ticket ticket) {
        checkNotNull(ticket);
        checkArgument(ticket.id() == null);

        long queryId = getQueryIdOrCreate(ticket.queryRef());
        AssigneeWrite assignee =
                toDbAssignee(ticket.assignedTo() != null ? ticket.assignedTo().id() : null);
        Long ticketId = dsl.insertInto(
                        TICKET,
                        TICKET.QUERY_ID,
                        TICKET.CREATED_MESSAGE_TS,
                        TICKET.STATUS,
                        TICKET.TEAM,
                        TICKET.IMPACT_CODE,
                        TICKET.LAST_INTERACTED_AT,
                        TICKET.ASSIGNED_TO,
                        TICKET.ASSIGNED_TO_FORMAT,
                        TICKET.ASSIGNED_TO_HASH)
                .values(
                        queryId,
                        ticket.createdMessageTs() != null
                                ? ticket.createdMessageTs().ts()
                                : null,
                        com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(
                                ticket.status().name()),
                        toDbTeam(ticket.team()),
                        ticket.impact(),
                        ticket.lastInteractedAt(),
                        assignee.value(),
                        assignee.format(),
                        assignee.hash())
                .onConflictDoNothing()
                .returning(TICKET.ID)
                .fetchOne(TICKET.ID);

        if (ticketId == null) {
            return Objects.requireNonNull(findTicketByQuery(ticket.queryRef()));
        }

        Ticket updatedTicket = ticket.withId(new TicketId(ticketId));

        updateTicketTags(updatedTicket);

        dsl.batchInsert(updatedTicket.statusLog().stream()
                        .map(l -> new TicketLogRecord()
                                .with(
                                        TICKET_LOG.TICKET_ID,
                                        checkNotNull(updatedTicket.id()).id())
                                .with(
                                        TICKET_LOG.EVENT,
                                        TicketEventType.lookupLiteral(l.status().name()))
                                .with(TICKET_LOG.DATE, l.date()))
                        .toList())
                .execute();

        return updatedTicket;
    }

    @Override
    public Ticket updateTicket(Ticket ticket) {
        checkNotNull(ticket);
        checkArgument(ticket.id() != null);

        var update = dsl.update(TICKET)
                .set(
                        TICKET.CREATED_MESSAGE_TS,
                        ticket.createdMessageTs() != null
                                ? ticket.createdMessageTs().ts()
                                : null)
                .set(
                        TICKET.STATUS,
                        com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(
                                ticket.status().name()))
                .set(TICKET.TEAM, toDbTeam(ticket.team()))
                .set(TICKET.IMPACT_CODE, ticket.impact())
                .set(TICKET.LAST_INTERACTED_AT, ticket.lastInteractedAt());

        update = conditionallyUpdateAssignee(ticket, update);

        int updatedTickets = update.where(TICKET.ID.eq(ticket.id().id())).execute();
        if (updatedTickets == 0) {
            log.atWarn().addArgument(ticket::id).log("No updated tickets found for ticket id {}");
            return ticket;
        }

        updateTicketTags(ticket);

        return ticket;
    }

    private UpdateSetMoreStep<TicketRecord> conditionallyUpdateAssignee(
            Ticket ticket, UpdateSetMoreStep<TicketRecord> update) {
        UpdateSetMoreStep<TicketRecord> result = update;
        if (ticket.assignedTo() != null) {
            AssigneeWrite assignee = toDbAssignee(ticket.assignedTo().id());
            if (assignee.value() != null) {
                result = result.set(TICKET.ASSIGNED_TO, assignee.value())
                        .set(TICKET.ASSIGNED_TO_FORMAT, assignee.format())
                        .set(TICKET.ASSIGNED_TO_HASH, assignee.hash());
            }
        }
        return result;
    }

    @Override
    public boolean touchTicketById(TicketId id, Instant timestamp) {
        checkNotNull(id);
        checkNotNull(timestamp);
        return dsl.update(TICKET)
                        .set(TICKET.LAST_INTERACTED_AT, timestamp)
                        .where(TICKET.ID.eq(id.id()))
                        .execute()
                > 0;
    }

    @Override
    public boolean assign(TicketId ticketId, String slackUserId) {
        return assignInternal(ticketId, slackUserId);
    }

    private boolean assignInternal(TicketId ticketId, String slackUserId) {
        checkNotNull(ticketId);
        checkNotNull(slackUserId);

        AssigneeWrite assignee = toDbAssignee(slackUserId);
        if (assignee.value() == null) {
            return false;
        }

        UpdateConditionStep<TicketRecord> update = dsl.update(TICKET)
                .set(TICKET.ASSIGNED_TO, assignee.value())
                .set(TICKET.ASSIGNED_TO_FORMAT, assignee.format())
                .set(TICKET.ASSIGNED_TO_HASH, assignee.hash())
                .where(TICKET.ID.eq(ticketId.id()));

        int updated = update.execute();

        return updated > 0;
    }

    private void updateTicketTags(Ticket ticket) {
        dsl.deleteFrom(TICKET_TO_TAG)
                .where(TICKET_TO_TAG
                        .TICKET_ID
                        .eq(checkNotNull(ticket.id()).id())
                        .and(TICKET_TO_TAG.TAG_CODE.notEqual(any(ticket.tags().toArray(String[]::new)))))
                .execute();

        dsl.insertInto(TICKET_TO_TAG, TICKET_TO_TAG.TICKET_ID, TICKET_TO_TAG.TAG_CODE)
                .valuesOfRows(ticket.tags().stream()
                        .map(t -> row(ticket.id().id(), t))
                        .toList())
                .onConflict(TICKET_TO_TAG.TICKET_ID, TICKET_TO_TAG.TAG_CODE)
                .doNothing()
                .execute();
    }

    @Transactional(readOnly = true)
    @Nullable @Override
    public Ticket findTicketById(TicketId ticketId) {
        var query = dsl.select(selectTicketFields())
                .from(TICKET)
                .join(QUERY)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(TICKET.ID.eq(ticketId.id()));
        return query.fetchOptional(this::buildTicketFromRow)
                .map(this::populateTicket)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    @Nullable @Override
    public Ticket findTicketByQuery(MessageRef queryRef) {
        var query = dsl.select(selectTicketFields())
                .from(TICKET)
                .join(QUERY)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(QUERY.TS.eq(queryRef.ts().ts()).and(QUERY.CHANNEL_ID.eq(queryRef.channelId())));
        return query.fetchOptional(this::buildTicketFromRow)
                .map(this::populateTicket)
                .orElse(null);
    }

    @Override
    public Ticket insertStatusLog(Ticket ticket, Instant at) {
        Ticket.StatusLog log = dsl.insertInto(TICKET_LOG, TICKET_LOG.TICKET_ID, TICKET_LOG.EVENT, TICKET_LOG.DATE)
                .values(
                        checkNotNull(ticket.id()).id(),
                        TicketEventType.lookupLiteral(ticket.status().name()),
                        at)
                .returning(TICKET_LOG.EVENT, TICKET_LOG.DATE)
                .fetchSingle(r ->
                        new Ticket.StatusLog(TicketStatus.valueOf(r.getEvent().getLiteral()), r.getDate()));
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

        List<Ticket> tickets = createFindQuery(query, selectTicketFields()).fetch(this::buildTicketFromRow);
        ImmutableList<Long> ids =
                tickets.stream().map(t -> checkNotNull(t.id()).id()).collect(toImmutableList());
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
            ticketsTotal = checkNotNull(createFindQuery(
                            query.toBuilder().order(null).unlimited(true).build(), ImmutableList.of(bigCount()))
                    .fetchOne(0, Long.class));
        }
        return new Page<>(
                content,
                query.page(),
                (int) Math.ceil((double) ticketsTotal
                        / query.pageSize()), // Convert to double before dividing, then use Math.ceil to round up
                ticketsTotal);
    }

    @Override
    public ImmutableList<TicketId> listStaleTicketIds(Instant checkAt, Duration timeToStale) {
        Instant stalenessThreshold = checkAt.minus(timeToStale);
        try (var stream = dsl
                .select(TICKET.ID)
                .from(TICKET)
                .where(TICKET.STATUS
                        .eq(com.coreeng.supportbot.dbschema.enums.TicketStatus.opened)
                        .and(TICKET.LAST_INTERACTED_AT.lt(stalenessThreshold)))
                .stream()) {
            return stream.map(r -> new TicketId(r.get(TICKET.ID))).collect(toImmutableList());
        }
    }

    @Override
    public ImmutableList<TicketId> listStaleTicketIdsToRemindOf(Instant checkAt, Duration reminderInterval) {
        Instant reminderThreshold = checkAt.minus(reminderInterval);
        try (var stream = dsl
                .select(TICKET.ID)
                .from(TICKET)
                .where(TICKET.STATUS
                        .eq(com.coreeng.supportbot.dbschema.enums.TicketStatus.stale)
                        .and(TICKET.LAST_INTERACTED_AT.lt(reminderThreshold)))
                .stream()) {
            return stream.map(r -> new TicketId(r.get(TICKET.ID))).collect(toImmutableList());
        }
    }

    private SelectLimitPercentAfterOffsetStep<Record> createFindQuery(
            TicketsQuery query, List<SelectField<?>> selectFields) {
        CommonTableExpression<?> taggedTicketsCTE = query.tags().isEmpty()
                ? null
                : name("taggedTickets")
                        .fields("id", "tags")
                        .as(selectDistinct(TICKET.ID, arrayAgg(TICKET_TO_TAG.TAG_CODE))
                                .from(TICKET)
                                .join(TICKET_TO_TAG)
                                .on(TICKET.ID
                                        .eq(TICKET_TO_TAG.TICKET_ID)
                                        .and(TICKET_TO_TAG.TAG_CODE.eq(
                                                any(query.tags().toArray(String[]::new)))))
                                .groupBy(TICKET.ID));
        return dsl.with(taggedTicketsCTE)
                .select(selectFields)
                .from(TICKET)
                .join(QUERY)
                .on(TICKET.QUERY_ID.eq(QUERY.ID))
                .where(ticketsQueryToCondition(query, taggedTicketsCTE))
                .orderBy(
                        switch (query.order()) {
                            case asc -> QUERY.DATE.asc();
                            case desc -> QUERY.DATE.desc();
                            case null -> noField();
                        })
                .limit(
                        query.unlimited() ? noField(Long.class) : value(query.page() * query.pageSize()),
                        query.unlimited() ? noField(Long.class) : value(query.pageSize()));
    }

    private Condition ticketsQueryToCondition(TicketsQuery query, @Nullable CommonTableExpression<?> taggedTicketsCTE) {
        Condition condition = noCondition();
        if (!query.ids().isEmpty()) {
            Long[] ids = query.ids().stream().map(TicketId::id).toArray(Long[]::new);
            condition = condition.and(TICKET.ID.eq(any(ids)));
        }
        if (query.status() != null) {
            condition = condition.and(TICKET.STATUS.eq(com.coreeng.supportbot.dbschema.enums.TicketStatus.lookupLiteral(
                    query.status().name())));
        }
        if (query.excludeClosed()) {
            condition = condition.and(TICKET.STATUS.ne(com.coreeng.supportbot.dbschema.enums.TicketStatus.closed));
        }
        if (query.dateFrom() != null) {
            Instant dateFrom = query.dateFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
            condition = condition.and(QUERY.DATE.ge(dateFrom));
        }
        if (query.dateTo() != null) {
            Instant dateTo = query.dateTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            condition = condition.and(QUERY.DATE.le(dateTo));
        }
        if (query.escalated() != null) {
            condition = condition.and(createIsEscalatedQuery().eq(query.escalated()));
        }
        if (!query.impacts().isEmpty()) {
            condition = condition.and(TICKET.IMPACT_CODE
                    .isNotNull()
                    .and(TICKET.IMPACT_CODE.eq(any(query.impacts().toArray(String[]::new)))));
        }
        if (!query.teams().isEmpty()) {
            condition = condition.and(
                    TICKET.TEAM.isNotNull().and(TICKET.TEAM.eq(any(query.teams().toArray(String[]::new)))));
        }
        if (query.includeNoTags() || !query.tags().isEmpty()) {
            Condition tagCondition = noCondition();

            if (query.includeNoTags()) {
                // Tickets with no tags
                tagCondition = tagCondition.or(
                        notExists(selectOne().from(TICKET_TO_TAG).where(TICKET_TO_TAG.TICKET_ID.eq(TICKET.ID))));
            }

            if (!query.tags().isEmpty()) {
                // Tickets that have all selected tags
                checkNotNull(taggedTicketsCTE);
                var taggedTicketIds = checkNotNull(taggedTicketsCTE.field("id", Long.class));
                var taggedTicketTags = checkNotNull(taggedTicketsCTE.field("tags", CLOB.array()));
                tagCondition = tagCondition.or(exists(selectOne()
                        .from(taggedTicketsCTE)
                        .where(TICKET.ID
                                .eq(taggedTicketIds)
                                .and(taggedTicketTags.contains(
                                        cast(query.tags().toArray(String[]::new), CLOB.array()))))));
            }

            condition = condition.and(tagCondition);
        }
        if (!Strings.isNullOrEmpty(query.escalationTeam())) {
            condition = condition.and(exists(selectOne()
                    .from(ESCALATION)
                    .where(ESCALATION.TICKET_ID.eq(TICKET.ID).and(ESCALATION.TEAM.eq(query.escalationTeam())))));
        }
        if (!Strings.isNullOrEmpty(query.assignedTo())) {
            // Filter by hash for efficient lookup (works with both plain and encrypted values)
            String filterHash = assigneeCrypto.computeHash(query.assignedTo());
            if (filterHash != null) {
                condition = condition.and(TICKET.ASSIGNED_TO_HASH.eq(filterHash));
            }
        }
        return condition;
    }

    private Ticket populateTicket(Ticket t) {
        TicketId id = checkNotNull(t.id());
        ImmutableList<Ticket.StatusLog> logs =
                fetchStatusLogs(ImmutableList.of(id.id())).get(id);
        ImmutableList<String> tags = fetchTags(ImmutableList.of(id.id())).get(id);

        return t.toBuilder().statusLog(logs).tags(tags).build();
    }

    private ImmutableListMultimap<TicketId, Ticket.StatusLog> fetchStatusLogs(ImmutableCollection<Long> ids) {
        try (var stream = dsl
                .select(TICKET_LOG.TICKET_ID, TICKET_LOG.EVENT, TICKET_LOG.DATE)
                .from(TICKET_LOG)
                .where(TICKET_LOG.TICKET_ID.eq(any(ids.toArray(Long[]::new))))
                .orderBy(TICKET_LOG.TICKET_ID, TICKET_LOG.DATE)
                .stream()) {
            return stream.collect(toImmutableListMultimap(
                    r -> new TicketId(r.get(TICKET_LOG.TICKET_ID)),
                    r -> new Ticket.StatusLog(
                            TicketStatus.valueOf(r.get(TICKET_LOG.EVENT).getLiteral()), r.get(TICKET_LOG.DATE))));
        }
    }

    private ImmutableListMultimap<TicketId, String> fetchTags(ImmutableCollection<Long> ids) {
        try (var stream = dsl
                .select(TICKET_TO_TAG.TICKET_ID, TICKET_TO_TAG.TAG_CODE)
                .from(TICKET_TO_TAG)
                .where(TICKET_TO_TAG.TICKET_ID.eq(any(ids.toArray(Long[]::new))))
                .stream()) {
            return stream.collect(toImmutableListMultimap(
                    r -> new TicketId(r.get(TICKET_TO_TAG.TICKET_ID)), r -> r.get(TICKET_TO_TAG.TAG_CODE)));
        }
    }

    private Condition createIsEscalatedQuery() {
        return exists(dsl.selectOne()
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(TICKET.ID).and(ESCALATION.STATUS.eq(EscalationStatus.opened))));
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
                TICKET.RATING_SUBMITTED,
                TICKET.ASSIGNED_TO,
                TICKET.ASSIGNED_TO_FORMAT,
                TICKET.LAST_INTERACTED_AT);
    }

    private Ticket buildTicketFromRow(Record r) {
        String assignedToFormat = r.get(TICKET.ASSIGNED_TO_FORMAT);
        String assignedPlain = decryptAssignee(r.get(TICKET.ASSIGNED_TO), assignedToFormat);

        return Ticket.builder()
                .id(new TicketId(r.get(TICKET.ID)))
                .queryTs(MessageTs.of(r.get(QUERY.TS)))
                .channelId(r.get(QUERY.CHANNEL_ID))
                .createdMessageTs(MessageTs.ofOrNull(r.get(TICKET.CREATED_MESSAGE_TS)))
                .status(TicketStatus.valueOf(r.get(TICKET.STATUS).getLiteral()))
                .team(fromDbTeam(r.get(TICKET.TEAM)))
                .impact(r.get(TICKET.IMPACT_CODE))
                .ratingSubmitted(r.get(TICKET.RATING_SUBMITTED))
                .assignedTo(assignedPlain != null ? SlackId.user(assignedPlain) : null)
                .lastInteractedAt(r.get(TICKET.LAST_INTERACTED_AT))
                .build();
    }

    private AssigneeWrite toDbAssignee(@Nullable String assigneePlain) {
        if (assigneePlain == null) {
            return new AssigneeWrite(null, "plain", null);
        }
        String hash = assigneeCrypto.computeHash(assigneePlain);
        return assigneeCrypto
                .encrypt(assigneePlain)
                .map(res -> new AssigneeWrite(res.value(), res.format(), hash))
                .orElseGet(() -> {
                    // encryption failed; skip assignment but do not break flow
                    return new AssigneeWrite(null, "plain", null);
                });
    }

    @Nullable private String decryptAssignee(@Nullable String stored, @Nullable String format) {
        if (stored == null) {
            return null;
        }
        return assigneeCrypto.decrypt(stored, format == null ? "plain" : format).orElse(null);
    }

    @Override
    public boolean isTicketRated(TicketId ticketId) {
        return dsl.select(TICKET.RATING_SUBMITTED)
                .from(TICKET)
                .where(TICKET.ID.eq(ticketId.id()))
                .fetchOptional()
                .map(r -> r.get(TICKET.RATING_SUBMITTED))
                .orElse(false);
    }

    @Override
    public void markTicketAsRated(TicketId ticketId) {
        dsl.update(TICKET)
                .set(TICKET.RATING_SUBMITTED, true)
                .where(TICKET.ID.eq(ticketId.id()))
                .execute();
    }

    @Nullable private String toDbTeam(@Nullable TicketTeam team) {
        if (team == null) {
            return null;
        }
        return switch (team) {
            case TicketTeam.KnownTeam known -> known.code();
            case TicketTeam.UnknownTeam _ -> TicketTeam.NOT_A_TENANT_CODE;
        };
    }

    @Nullable private TicketTeam fromDbTeam(@Nullable String team) {
        if (team == null) {
            return null;
        }
        if (TicketTeam.NOT_A_TENANT_CODE.equals(team)) {
            return new TicketTeam.UnknownTeam();
        }
        return new TicketTeam.KnownTeam(team);
    }
}
