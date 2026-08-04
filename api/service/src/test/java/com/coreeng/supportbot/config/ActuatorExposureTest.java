package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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

    // Binds through Spring's own yaml loading, so every notation Spring accepts at runtime is guarded.
    private static List<String> exposedEndpoints(Resource configFile) throws IOException {
        List<String> endpoints = new ArrayList<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(configFile.getFilename(), configFile)) {
            new Binder(ConfigurationPropertySources.from(source))
                    .bind(EXPOSURE_KEY, Bindable.of(String[].class))
                    .ifBound(values -> endpoints.addAll(List.of(values)));
        }
        return endpoints;
    }
}
