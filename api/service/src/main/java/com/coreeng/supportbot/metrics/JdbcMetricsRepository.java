package com.coreeng.supportbot.metrics;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.selectOne;

import static com.coreeng.supportbot.dbschema.Tables.ESCALATION;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import com.coreeng.supportbot.dbschema.enums.EscalationStatus;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcMetricsRepository implements MetricsRepository {

    private final DSLContext dsl;

    @Override
    public List<TicketMetric> getTicketMetrics() {
        Field<Boolean> escalated = exists(
            selectOne()
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(TICKET.ID))
                .and(ESCALATION.STATUS.ne(EscalationStatus.resolved))
        ).as("escalated");

        return dsl.select(TICKET.STATUS, TICKET.IMPACT_CODE, TICKET.TEAM, escalated, TICKET.RATING_SUBMITTED, count().as("count"))
            .from(TICKET)
            .groupBy(TICKET.STATUS, TICKET.IMPACT_CODE, TICKET.TEAM, escalated, TICKET.RATING_SUBMITTED)
            .fetch(r -> new TicketMetric(
                r.get(TICKET.STATUS).getLiteral(),
                r.get(TICKET.IMPACT_CODE) != null ? r.get(TICKET.IMPACT_CODE) : "unknown",
                r.get(TICKET.TEAM) != null ? r.get(TICKET.TEAM) : "unassigned",
                r.get(escalated),
                r.get(TICKET.RATING_SUBMITTED),
                r.get("count", Long.class)
            ));
    }

    @Override
    public List<EscalationMetric> getEscalationMetrics() {
        return dsl.select(ESCALATION.STATUS, ESCALATION.TEAM, TICKET.IMPACT_CODE, count().as("count"))
                .from(ESCALATION)
                .join(TICKET).on(ESCALATION.TICKET_ID.eq(TICKET.ID))
                .groupBy(ESCALATION.STATUS, ESCALATION.TEAM, TICKET.IMPACT_CODE)
                .fetch(r -> new EscalationMetric(
                        r.get(ESCALATION.STATUS).getLiteral(),
                        r.get(ESCALATION.TEAM) != null ? r.get(ESCALATION.TEAM) : "unknown",
                        r.get(TICKET.IMPACT_CODE) != null ? r.get(TICKET.IMPACT_CODE) : "unknown",
                        r.get("count", Long.class)
                ));
    }
}
