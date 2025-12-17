package com.coreeng.supportbot.config;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("useful-links")
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
