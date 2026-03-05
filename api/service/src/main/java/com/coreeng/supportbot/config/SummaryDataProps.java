package com.coreeng.supportbot.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "summary-data")
public record SummaryDataProps(@DefaultValue SanitisationProperties sanitisation) {

    public record SanitisationProperties(
            @DefaultValue List<String> patterns,
            @DefaultValue List<String> exceptions) {}
}
