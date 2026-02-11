package com.coreeng.supportbot.teams.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public record Config(ServiceConfig service, String namespace, PortForwardingConfig portForwarding) {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public record DeploymentScriptConfig(
            String releaseName, String chartPath, String valuesFilePath, String scriptPath) {}

    public record ImageConfig(
            @JsonProperty("repository") String repository,
            @JsonProperty("tag") String tag) {}

    public record ServiceConfig(
            @JsonProperty("deployment") DeploymentConfig deployment,
            @JsonProperty("image") ImageConfig image,
            @JsonProperty("deploymentScript") DeploymentScriptConfig deploymentScript) {}

    public record PortForwardingConfig(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("localPort") int localPort,
            @JsonProperty("remotePort") int remotePort) {}

    public record DeploymentConfig(String name) {}

    private static Config load(String configPath) throws IOException {
        InputStream inputStream = null;
        try {
            if (Files.exists(Paths.get(configPath))) {
                inputStream = new FileInputStream(configPath);
            } else {
                inputStream = Config.class.getClassLoader().getResourceAsStream(configPath);
            }

            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Configuration file not found in filesystem or classpath: " + configPath);
            }

            return YAML_MAPPER.readValue(inputStream, Config.class);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    public static Config load() throws IOException {
        String configPath = System.getenv().getOrDefault("INTEGRATION_TEST_CONFIG", "integration-test-local.yaml");
        return load(configPath);
    }
}
