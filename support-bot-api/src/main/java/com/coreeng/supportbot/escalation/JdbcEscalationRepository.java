package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.dbschema.enums.EscalationEventType;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
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

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
public class JdbcEscalationRepository implements EscalationRepository {
    private final DSLContext dsl;
    private final ZoneId timezone;

    @Nullable
    @Override
    public Escalation createIfNotExists(Escalation escalation) {
        checkNotNull(escalation);
        checkArgument(escalation.id() == null);

        Long id = dsl.insertInto(
                ESCALATION,
                ESCALATION.TICKET_ID,
                ESCALATION.CHANNEL_ID,
                ESCALATION.THREAD_TS,
                ESCALATION.CREATED_MESSAGE_TS,
                ESCALATION.STATUS,
                ESCALATION.TEAM
            ).values(
                escalation.ticketId().id(),
                escalation.channelId(),
                escalation.threadTs() != null
                    ? escalation.threadTs().ts()
                    : null,
                escalation.createdMessageTs() != null
                    ? escalation.createdMessageTs().ts()
                    : null,
                com.coreeng.supportbot.dbschema.enums.EscalationStatus.lookupLiteral(escalation.status().name()),
                escalation.team()
            )
            .onConflict(ESCALATION.THREAD_TS, ESCALATION.CHANNEL_ID).doNothing()
            .returning(ESCALATION.ID)
            .fetchOne(ESCALATION.ID);
        if (id == null) {
            log.atWarn()
                .addArgument(escalation::threadTs)
                .log("Couldn't insert escalation with threadTs({})");
            return escalation;
        }

        dsl.insertInto(ESCALATION_TO_TAG, ESCALATION_TO_TAG.ESCALATION_ID, ESCALATION_TO_TAG.TAG_CODE)
            .valuesOfRows(
                escalation.tags().stream()
                    .map(t -> row(id, t))
                    .toList()
            )
            .execute();

        dsl.insertInto(
                ESCALATION_LOG,
                ESCALATION_LOG.ESCALATION_ID,
                ESCALATION_LOG.EVENT,
                ESCALATION_LOG.DATE
            ).values(id, EscalationEventType.opened, escalation.openedAt())
            .execute();

        return escalation.toBuilder()
            .id(new EscalationId(id))
            .build();
    }

    @Override
    public Escalation update(Escalation escalation) {
        checkNotNull(escalation);
        checkNotNull(escalation.id());

        int updatedRows = dsl.update(ESCALATION)
            .set(ESCALATION.CHANNEL_ID, escalation.channelId())
            .set(ESCALATION.THREAD_TS,
                escalation.threadTs() != null
                    ? escalation.threadTs().ts()
                    : null)
            .set(ESCALATION.CREATED_MESSAGE_TS, escalation.createdMessageTs() != null
                ? escalation.createdMessageTs().ts()
                : null)
            .set(ESCALATION.STATUS, com.coreeng.supportbot.dbschema.enums.EscalationStatus.lookupLiteral(escalation.status().name()))
            .set(ESCALATION.TEAM, escalation.team())
            .where(ESCALATION.ID.eq(escalation.id().id()))
            .execute();
        if (updatedRows == 0) {
            log.atWarn()
                .addArgument(escalation::id)
                .log("No updated escalation with id {}");
        }
        return escalation;
    }

    @Override
    public Escalation markResolved(Escalation escalation, Instant at) {
        checkNotNull(escalation);
        checkNotNull(escalation.id());
        checkArgument(escalation.status() != EscalationStatus.resolved);
        checkArgument(escalation.resolvedAt() == null);

        int escalationChanged = dsl.update(ESCALATION)
            .set(ESCALATION.STATUS, com.coreeng.supportbot.dbschema.enums.EscalationStatus.resolved)
            .where(ESCALATION.ID.eq(escalation.id().id()).and(
                ESCALATION.STATUS.notEqual(com.coreeng.supportbot.dbschema.enums.EscalationStatus.resolved)
            ))
            .execute();
        if (escalationChanged == 0) {
            return findById(escalation.id());
        }

        dsl.insertInto(
                ESCALATION_LOG,
                ESCALATION_LOG.ESCALATION_ID,
                ESCALATION_LOG.EVENT,
                ESCALATION_LOG.DATE
            ).values(escalation.id().id(), EscalationEventType.resolved, at)
            .execute();

        return escalation.toBuilder()
            .resolvedAt(at)
            .status(EscalationStatus.resolved)
            .build();
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsByThreadTs(MessageTs threadTs) {
        return dsl.fetchExists(
            ESCALATION,
            ESCALATION.THREAD_TS.eq(threadTs.ts())
        );
    }

    @Transactional(readOnly = true)
    @Override
    public long countNotResolvedByTicketId(TicketId ticketId) {
        return checkNotNull(
            dsl.select(bigCount())
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(ticketId.id())
                    .and(ESCALATION.STATUS.ne(com.coreeng.supportbot.dbschema.enums.EscalationStatus.resolved)))
                .fetchOne(0, Long.class)
        );
    }

    @Transactional(readOnly = true)
    @Nullable
    @Override
    public Escalation findById(EscalationId id) {
        return dsl.select(getSelectFields())
            .from(ESCALATION)
            .where(ESCALATION.ID.eq(id.id()))
            .fetchOptional(this::buildEscalationFromRow)
            .map(this::populateEscalation)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    @Override
    public ImmutableList<Escalation> listByTicketId(TicketId ticketId) {
        return listByCondition(ESCALATION.TICKET_ID.eq(ticketId.id()), null, null);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Escalation> findByQuery(EscalationQuery query) {
        Condition condition = queryToCondition(query);

        Long offset = query.page() * query.pageSize();
        Long limit = query.pageSize();
        ImmutableList<Escalation> content = listByCondition(condition, offset, limit);

        long totalEscalations = checkNotNull(
            dsl.select(bigCount())
                .from(ESCALATION)
                .where(condition)
                .fetchOne(0, Long.class)
        );
        return new Page<>(
            content,
            query.page(),
            totalEscalations / query.pageSize() + 1,
            totalEscalations
        );
    }

    private Condition queryToCondition(EscalationQuery query) {
        Condition condition = noCondition();
        if (!query.ids().isEmpty()) {
            Long[] ids = query.ids().stream()
                .map(EscalationId::id)
                .toArray(Long[]::new);
            condition = condition.and(ESCALATION.ID.eq(any(ids)));
        }
        if (query.ticketId() != null) {
            condition = condition.and(ESCALATION.TICKET_ID.eq(query.ticketId().id()));
        }
        if (query.dateFrom() != null) {
            Instant dateFrom = query.dateFrom().atStartOfDay(timezone).toInstant();
            condition = condition.and(exists(
                selectOne()
                    .from(
                        select(ESCALATION_LOG.DATE)
                            .from(ESCALATION_LOG)
                            .where(ESCALATION_LOG.ESCALATION_ID.eq(ESCALATION.ID))
                            .orderBy(ESCALATION_LOG.ID)
                            .limit(1)
                    ).where(ESCALATION_LOG.DATE.ge(dateFrom))
            ));
        }
        if (query.dateTo() != null) {
            Instant dateTo = query.dateTo().plusDays(1).atStartOfDay(timezone).toInstant();
            condition = condition.and(exists(
                selectOne()
                    .from(
                        select(ESCALATION_LOG.DATE)
                            .from(ESCALATION_LOG)
                            .where(ESCALATION_LOG.ESCALATION_ID.eq(ESCALATION.ID))
                            .orderBy(ESCALATION_LOG.ID.desc())
                            .limit(1)
                    ).where(ESCALATION_LOG.DATE.le(dateTo))
            ));
        }
        if (query.status() != null) {
            condition = condition.and(ESCALATION.STATUS.eq(
                com.coreeng.supportbot.dbschema.enums.EscalationStatus.lookupLiteral(query.status().name())
            ));
        }
        if (query.team() != null) {
            condition = condition.and(ESCALATION.TEAM.eq(query.team()));
        }
        return condition;
    }

    private ImmutableList<Escalation> listByCondition(
        Condition condition,
        @Nullable Long offset,
        @Nullable Long limit
    ) {
        List<Escalation> escalations = dsl.select(getSelectFields())
            .from(ESCALATION)
            .where(condition)
            .limit(
                offset != null
                    ? value(offset)
                    : noField(Long.class),
                limit != null
                    ? value(limit)
                    : noField(Long.class)
            )
            .fetch(this::buildEscalationFromRow);
        ImmutableList<Long> ids = escalations.stream()
            .map(e -> checkNotNull(e.id()).id())
            .collect(toImmutableList());

        ImmutableListMultimap<EscalationId, Log> logsById = fetchLogs(ids);
        ImmutableListMultimap<EscalationId, String> tagsById = fetchTags(ids);

        return escalations.stream()
            .map(e -> {
                ImmutableList<Log> logs = logsById.get(checkNotNull(e.id()));
                return e.toBuilder()
                    .tags(tagsById.get(e.id()))
                    .openedAt(logs.getFirst().date())
                    .resolvedAt(logs.size() > 1 ? logs.getLast().date() : null)
                    .build();
            })
            .collect(toImmutableList());
    }

    private Escalation populateEscalation(Escalation e) {
        long rawId = checkNotNull(e.id()).id();
        ImmutableList<Log> logs = fetchLogs(ImmutableList.of(rawId))
            .get(e.id());
        ImmutableList<String> tags = fetchTags(ImmutableList.of(rawId))
            .get(e.id());

        return e.toBuilder()
            .openedAt(logs.getFirst().date())
            .resolvedAt(logs.size() > 1
                ? logs.getLast().date()
                : null)
            .tags(tags)
            .build();
    }

    private ImmutableListMultimap<EscalationId, Log> fetchLogs(ImmutableCollection<Long> ids) {
        if (ids.isEmpty()) {
            return ImmutableListMultimap.of();
        }
        try (var stream = dsl.select(ESCALATION_LOG.ESCALATION_ID, ESCALATION_LOG.EVENT, ESCALATION_LOG.DATE)
            .from(ESCALATION_LOG)
            .where(ESCALATION_LOG.ESCALATION_ID.eq(any(ids.toArray(Long[]::new))))
            .orderBy(ESCALATION_LOG.ESCALATION_ID, ESCALATION_LOG.DATE)
            .stream()) {
            return stream.collect(toImmutableListMultimap(
                r -> new EscalationId(r.get(ESCALATION_LOG.ESCALATION_ID)),
                r -> new Log(
                    EscalationStatus.valueOf(r.get(ESCALATION_LOG.EVENT).getLiteral()),
                    r.get(ESCALATION_LOG.DATE)
                )
            ));
        }
    }

    private ImmutableListMultimap<EscalationId, String> fetchTags(ImmutableCollection<Long> ids) {
        if (ids.isEmpty()) {
            return ImmutableListMultimap.of();
        }
        try (var stream = dsl.select(ESCALATION_TO_TAG.ESCALATION_ID, ESCALATION_TO_TAG.TAG_CODE)
            .from(ESCALATION_TO_TAG)
            .where(ESCALATION_TO_TAG.ESCALATION_ID.eq(any(ids.toArray(Long[]::new))))
            .stream()) {
            return stream.collect(toImmutableListMultimap(
                r -> new EscalationId(r.get(ESCALATION_TO_TAG.ESCALATION_ID)),
                r -> r.get(ESCALATION_TO_TAG.TAG_CODE)
            ));
        }
    }

    private Escalation buildEscalationFromRow(Record r) {
        return Escalation.builder()
            .id(new EscalationId(r.get(ESCALATION.ID)))
            .ticketId(new TicketId(r.get(ESCALATION.TICKET_ID)))
            .channelId(r.get(ESCALATION.CHANNEL_ID))
            .threadTs(MessageTs.ofOrNull(r.get(ESCALATION.THREAD_TS)))
            .createdMessageTs(MessageTs.ofOrNull(r.get(ESCALATION.CREATED_MESSAGE_TS)))
            .status(EscalationStatus.valueOf(r.get(ESCALATION.STATUS).getLiteral()))
            .team(r.get(ESCALATION.TEAM))
            .build();
    }

    private ImmutableList<SelectField<?>> getSelectFields() {
        return ImmutableList.of(
            ESCALATION.ID,
            ESCALATION.TICKET_ID,
            ESCALATION.CHANNEL_ID,
            ESCALATION.THREAD_TS,
            ESCALATION.CREATED_MESSAGE_TS,
            ESCALATION.STATUS,
            ESCALATION.TEAM
        );
    }

    record Log(EscalationStatus event, Instant date) {
    }
}
