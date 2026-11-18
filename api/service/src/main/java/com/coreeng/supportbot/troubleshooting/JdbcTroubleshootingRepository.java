package com.coreeng.supportbot.troubleshooting;

import static com.coreeng.supportbot.dbschema.Tables.TROUBLESHOOTING_EVENT;
import static com.coreeng.supportbot.dbschema.Tables.TROUBLESHOOTING_SESSION;

import com.coreeng.supportbot.dbschema.tables.records.TroubleshootingEventRecord;
import com.coreeng.supportbot.dbschema.tables.records.TroubleshootingSessionRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnTroubleshootingEnabled
@RequiredArgsConstructor
public class JdbcTroubleshootingRepository implements TroubleshootingRepository {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public @Nullable TroubleshootingSession findByTicketId(long ticketId) {
        return dsl.selectFrom(TROUBLESHOOTING_SESSION)
                .where(TROUBLESHOOTING_SESSION.TICKET_ID.eq(ticketId))
                .fetchOne(JdbcTroubleshootingRepository::toSession);
    }

    @Override
    @Transactional
    public boolean createIfAbsent(long ticketId) {
        return dsl.insertInto(TROUBLESHOOTING_SESSION)
                        .set(TROUBLESHOOTING_SESSION.TICKET_ID, ticketId)
                        .set(TROUBLESHOOTING_SESSION.MODE, TroubleshootingMode.MANUAL.name())
                        .onConflict(TROUBLESHOOTING_SESSION.TICKET_ID)
                        .doNothing()
                        .execute()
                > 0;
    }

    @Override
    @Transactional
    public TroubleshootingEvent append(long sessionId, TroubleshootingEventType type, String actor, Object payload) {
        Long seq = dsl.update(TROUBLESHOOTING_SESSION)
                .set(TROUBLESHOOTING_SESSION.LAST_SEQ, TROUBLESHOOTING_SESSION.LAST_SEQ.plus(1))
                .where(TROUBLESHOOTING_SESSION.ID.eq(sessionId))
                .returning(TROUBLESHOOTING_SESSION.LAST_SEQ)
                .fetchSingle()
                .getLastSeq();
        TroubleshootingEventRecord row = dsl.insertInto(TROUBLESHOOTING_EVENT)
                .set(TROUBLESHOOTING_EVENT.SESSION_ID, sessionId)
                .set(TROUBLESHOOTING_EVENT.SEQ, seq)
                .set(TROUBLESHOOTING_EVENT.TYPE, type.name())
                .set(TROUBLESHOOTING_EVENT.ACTOR, actor)
                .set(TROUBLESHOOTING_EVENT.PAYLOAD, JSONB.valueOf(toJson(payload)))
                .set(TROUBLESHOOTING_EVENT.CREATED_AT, Instant.now())
                .returning()
                .fetchSingle();
        return toEvent(row);
    }

    @Override
    @Transactional(readOnly = true)
    public ImmutableList<TroubleshootingEvent> findEventsAfter(long sessionId, long afterSeq) {
        return dsl
                .selectFrom(TROUBLESHOOTING_EVENT)
                .where(TROUBLESHOOTING_EVENT.SESSION_ID.eq(sessionId))
                .and(TROUBLESHOOTING_EVENT.SEQ.gt(afterSeq))
                .orderBy(TROUBLESHOOTING_EVENT.SEQ)
                .fetch(this::toEvent)
                .stream()
                .collect(ImmutableList.toImmutableList());
    }

    private static TroubleshootingSession toSession(TroubleshootingSessionRecord row) {
        return new TroubleshootingSession(
                row.getId(),
                row.getTicketId(),
                TroubleshootingMode.valueOf(row.getMode()),
                row.getLastSeq(),
                row.getCreatedAt());
    }

    private TroubleshootingEvent toEvent(TroubleshootingEventRecord row) {
        try {
            return new TroubleshootingEvent(
                    row.getSessionId(),
                    row.getSeq(),
                    TroubleshootingEventType.valueOf(row.getType()),
                    row.getActor(),
                    objectMapper.readTree(row.getPayload().data()),
                    row.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable payload for event " + row.getSeq(), e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload cannot be serialised", e);
        }
    }
}
