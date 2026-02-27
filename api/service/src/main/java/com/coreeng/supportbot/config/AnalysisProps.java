package com.coreeng.supportbot.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis")
public record AnalysisProps(Vertex vertex, Prompt prompt) {
    public record Vertex(String projectId, String location, String modelName, Duration requestDelay) {}

    public record Prompt(String file, String idFile, String prompt) {
        public Prompt {
            try {
                prompt = Files.readString(Path.of(file));
            } catch (IOException e) {
                throw new RuntimeException("Unable to load prompt file: " + file, e);
            }
        }
    }
}
