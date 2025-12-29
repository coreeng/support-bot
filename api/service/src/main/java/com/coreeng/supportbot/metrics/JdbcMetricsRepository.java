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
    public List<TicketMetricRow> getTicketMetrics() {
        Field<String> status = TICKET.STATUS.cast(String.class).as("status");
        Field<String> impact = TICKET.IMPACT_CODE.as("impact");
        Field<String> team = TICKET.TEAM.as("team");
        Field<Boolean> escalated = exists(
            selectOne()
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(TICKET.ID))
                .and(ESCALATION.STATUS.ne(EscalationStatus.resolved))
        ).as("escalated");
        Field<Boolean> rated = TICKET.RATING_SUBMITTED.as("rated");

        return dsl.select(status, impact, team, escalated, rated, count().as("count"))
            .from(TICKET)
            .groupBy(status, impact, team, escalated, rated)
            .fetch(r -> new TicketMetricRow(
                r.get(status),
                r.get(impact) != null ? r.get(impact) : "unknown",
                r.get(team) != null ? r.get(team) : "unassigned",
                r.get(escalated),
                r.get(rated),
                r.get("count", Long.class)
            ));
    }
}
