package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.jspecify.annotations.Nullable;
import java.util.List;

@ConfigurationProperties("support-insights")
public record SupportInsightsProps(List<Dashboard> dashboards) {
    public SupportInsightsProps(List<Dashboard> dashboards) {
        this.dashboards = dashboards == null ? List.of() : List.copyOf(dashboards);
    }

    public record Dashboard(
        String title,
        String url,
        @Nullable String description
    ) {
    }
}
