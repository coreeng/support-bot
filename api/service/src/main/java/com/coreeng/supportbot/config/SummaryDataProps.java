package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "summary-data")
public record SummaryDataProps(String analysisBundlePath) {}
