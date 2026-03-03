package com.coreeng.supportbot.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record PrTrackingRepositoryProps(String name, String owningTeam, Duration sla, List<String> tenantPathGlobs) {

    public PrTrackingRepositoryProps(String name, String owningTeam, Duration sla, @Nullable List<String> tenantPathGlobs) {
        this.name = name;
        this.owningTeam = owningTeam;
        this.sla = sla;
        this.tenantPathGlobs = deduplicateTenantPathGlobs(tenantPathGlobs);
    }

    public PrTrackingRepositoryProps(String name, String owningTeam, Duration sla) {
        this(name, owningTeam, sla, List.of());
    }

    private static List<String> deduplicateTenantPathGlobs(@Nullable List<String> tenantPathGlobs) {
        if (tenantPathGlobs == null) {
            return List.of();
        }

        Map<String, String> deduplicatedByNormalizedValue = new LinkedHashMap<>();
        for (String tenantPathGlob : tenantPathGlobs) {
            String trimmed = tenantPathGlob == null ? null : tenantPathGlob.trim();
            String normalized = trimmed == null ? "" : trimmed.toLowerCase(Locale.ROOT);
            deduplicatedByNormalizedValue.putIfAbsent(normalized, trimmed);
        }
        return List.copyOf(deduplicatedByNormalizedValue.values());
    }
}
