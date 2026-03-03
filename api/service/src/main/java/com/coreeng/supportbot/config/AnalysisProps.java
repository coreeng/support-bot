package com.coreeng.supportbot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis")
public record AnalysisProps(Vertex vertex, Bundle bundle, Prompt prompt) {
    public record Vertex(String projectId, String location, String modelName, Duration requestDelay) {}

    public record Bundle(String path) {}

    public record Prompt(boolean enabled, String file, String idFile) {}
}
