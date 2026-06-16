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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Detects stored references to retired/removed enum codes and surfaces them loudly: an ERROR log
 * plus a Micrometer gauge {@code support_bot.orphaned_references} tagged by type (PT-518).
 *
 * <p>Non-fatal by design — such references are expected after a legitimate retire/rename and are
 * rendered gracefully at runtime (M1); we only want operators to notice them, never to block a
 * restart (pre-existing orphans must not brick prod). The whole scan is guarded so a query problem
 * can never prevent startup. Runs after {@code RegistryInitialisation} so soft-deletes are applied.
 */
@Component
@Order(102)
@RequiredArgsConstructor
@Slf4j
public class OrphanReferenceScanner implements ApplicationRunner {

    private final DSLContext dsl;
    private final EnumProps enumProps;
    private final MeterRegistry meterRegistry;

    @Override
    public void run(ApplicationArguments args) {
        try {
            long retiredImpactRefs = dsl.fetchCount(
                    TICKET,
                    TICKET.IMPACT_CODE.in(
                            dsl.select(IMPACT.CODE).from(IMPACT).where(IMPACT.DELETED_AT.isNotNull())));
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
                        .log("Found stored references to retired/removed enum codes; they render with their "
                                + "last-known label. Rename a code back in config to re-link, or ignore if intentional.");
            } else {
                log.atInfo().log("No orphaned enum references found");
            }
        } catch (RuntimeException e) {
            // A diagnostic scan must never block startup.
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
        Gauge.builder("support_bot.orphaned_references", () -> count)
                .description("Stored references to retired/removed enum codes")
                .tag("type", type)
                .strongReference(true)
                .register(meterRegistry);
    }
}
