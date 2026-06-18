package com.coreeng.supportbot.enums;

import static com.coreeng.supportbot.dbschema.Tables.ESCALATION;
import static com.coreeng.supportbot.dbschema.Tables.ESCALATION_TO_TAG;
import static com.coreeng.supportbot.dbschema.Tables.IMPACT;
import static com.coreeng.supportbot.dbschema.Tables.TAG;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static com.coreeng.supportbot.dbschema.Tables.TICKET_TO_TAG;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.config.EnumProps;
import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(102)
@RequiredArgsConstructor
@Slf4j
public class OrphanReferenceScanner implements ApplicationRunner {

    private final DSLContext dsl;
    private final EnumProps enumProps;
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> orphanGauges = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        try {
            long retiredImpactRefs = dsl.fetchCount(
                    TICKET,
                    TICKET.IMPACT_CODE.in(dsl.select(IMPACT.CODE).from(IMPACT).where(IMPACT.DELETED_AT.isNotNull())));
            long retiredTagRefs = (long) dsl.fetchCount(
                            TICKET_TO_TAG,
                            TICKET_TO_TAG.TAG_CODE.in(
                                    dsl.select(TAG.CODE).from(TAG).where(TAG.DELETED_AT.isNotNull())))
                    + dsl.fetchCount(
                            ESCALATION_TO_TAG,
                            ESCALATION_TO_TAG.TAG_CODE.in(
                                    dsl.select(TAG.CODE).from(TAG).where(TAG.DELETED_AT.isNotNull())));
            long orphanedEscalationTeamRefs = dsl.fetchCount(ESCALATION, orphanedEscalationTeamCondition());

            register("impact", retiredImpactRefs);
            register("tag", retiredTagRefs);
            register("escalation_team", orphanedEscalationTeamRefs);

            long total = retiredImpactRefs + retiredTagRefs + orphanedEscalationTeamRefs;
            if (total > 0) {
                log.atError()
                        .addKeyValue("retiredImpactRefs", retiredImpactRefs)
                        .addKeyValue("retiredTagRefs", retiredTagRefs)
                        .addKeyValue("orphanedEscalationTeamRefs", orphanedEscalationTeamRefs)
                        .log(
                                "Found stored references to retired/removed enum codes; they render with their "
                                        + "last-known label. Rename a code back in config to re-link, or ignore if intentional.");
            } else {
                log.atInfo().log("No orphaned enum references found");
            }
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).log("Orphaned-reference scan failed; skipping (non-fatal)");
        }
    }

    private Condition orphanedEscalationTeamCondition() {
        ImmutableList<String> activeCodes =
                enumProps.escalationTeams().stream().map(EscalationTeam::code).collect(toImmutableList());
        if (activeCodes.isEmpty()) {
            return ESCALATION.TEAM.isNotNull();
        }
        return ESCALATION.TEAM.isNotNull().and(ESCALATION.TEAM.notIn(activeCodes));
    }

    private void register(String type, long count) {
        orphanGauges
                .computeIfAbsent(type, key -> {
                    AtomicLong holder = new AtomicLong();
                    Gauge.builder("support_bot.orphaned_references", holder, AtomicLong::get)
                            .description("Stored references to retired/removed enum codes")
                            .tag("type", key)
                            .strongReference(true)
                            .register(meterRegistry);
                    return holder;
                })
                .set(count);
    }
}
