package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.Nullable;
import java.util.List;

@ConfigurationProperties("support-insights")
public record SupportInsightsProps(List<Dashboard> dashboards) {

    public record Dashboard(
        String title,
        String url,
        @Nullable String description
    ) {
    }
}
