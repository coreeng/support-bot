package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

class ActuatorExposureTest {

    private static final String EXPOSURE_KEY = "management.endpoints.web.exposure.include";
    private static final Set<String> ALLOWED_ENDPOINTS = Set.of("health", "prometheus");

    @Test
    void applicationYamlFilesExposeOnlyHealthAndPrometheus() throws IOException {
        Resource[] configFiles = new PathMatchingResourcePatternResolver().getResources("classpath*:application*.yaml");

        assertThat(configFiles)
                .extracting(Resource::getFilename)
                .contains("application.yaml", "application-functionaltests.yaml");

        List<String> violations = new ArrayList<>();
        for (Resource configFile : configFiles) {
            for (String endpoint : exposedEndpoints(configFile)) {
                if (!ALLOWED_ENDPOINTS.contains(endpoint.toLowerCase(Locale.ROOT))) {
                    violations.add(configFile.getFilename() + " exposes actuator endpoint '" + endpoint + "'");
                }
            }
        }

        assertThat(violations)
                .as("Only the health and prometheus actuator endpoints may be exposed. Endpoints such as env,"
                        + " configprops, beans and heapdump can leak secrets (LLM proxy basic-auth token, DB and"
                        + " Slack credentials); review any addition for secret exposure before allowing it here.")
                .isEmpty();
    }

    private static List<String> exposedEndpoints(Resource configFile) throws IOException {
        List<String> endpoints = new ArrayList<>();
        try (InputStream input = configFile.getInputStream()) {
            for (Object document : new Yaml().loadAll(input)) {
                Map<String, Object> flattened = new HashMap<>();
                flatten("", document, flattened);
                endpoints.addAll(endpointNames(flattened.get(EXPOSURE_KEY)));
            }
        }
        return endpoints;
    }

    // Treats nested maps and literal dotted keys identically, like Spring's yaml binding does.
    private static void flatten(String prefix, Object node, Map<String, Object> target) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) ->
                    flatten(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key, value, target));
        } else if (!prefix.isEmpty()) {
            target.put(prefix, node);
        }
    }

    private static List<String> endpointNames(Object includeValue) {
        if (includeValue == null) {
            return List.of();
        }
        if (includeValue instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).toList();
        }
        return Arrays.stream(String.valueOf(includeValue).split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }
}
