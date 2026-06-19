package com.coreeng.supportbot.enums;

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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(102)
@RequiredArgsConstructor
@Slf4j
public class OrphanReferenceScanner implements ApplicationRunner {

    private final OrphanReferenceRepository repository;
    private final EnumProps enumProps;
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> orphanGauges = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        try {
            ImmutableList<String> activeEscalationCodes = enumProps.escalationTeams().stream()
                    .map(EscalationTeam::code)
                    .collect(toImmutableList());

            long retiredImpactRefs = repository.countRetiredImpactReferences();
            long retiredTagRefs = repository.countRetiredTagReferences();
            long orphanedEscalationTeamRefs = repository.countOrphanedEscalationTeamReferences(activeEscalationCodes);

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
