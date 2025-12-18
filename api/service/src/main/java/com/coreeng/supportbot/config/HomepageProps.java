package com.coreeng.supportbot.config;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("homepage")
public record HomepageProps(List<UsefulLink> usefulLinks) {
    public HomepageProps(List<UsefulLink> usefulLinks) {
        this.usefulLinks = usefulLinks == null ? List.of() : List.copyOf(usefulLinks);
    }

    public record UsefulLink(
        String title,
        String url,
        @Nullable String description
    ) {
    }
}
