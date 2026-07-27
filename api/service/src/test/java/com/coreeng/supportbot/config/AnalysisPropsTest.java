package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.config.AnalysisProps.Llm;
import com.coreeng.supportbot.config.AnalysisProps.LlmProvider;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AnalysisPropsTest {

    private static final String BASE64_TOKEN = "dXNlcjpwYXNz";

    @Test
    void defaultsToVertexProviderWithoutRequiringGatewaySettings() {
        Llm llm = bind(vertexValues());

        assertThat(llm.provider()).isEqualTo(LlmProvider.VERTEX);
        assertThat(llm.modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(llm.requestDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(llm.vertex().projectId()).isEqualTo("test-project");
        assertThat(llm.vertex().location()).isEqualTo("europe-west2");
        assertThat(llm.gateway().baseUrl()).isEmpty();
        assertThat(llm.gateway().basicAuthToken()).isEmpty();
        assertThat(llm.gateway().timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void vertexModeRequiresProjectId() {
        Map<String, Object> values = vertexValues();
        values.remove("analysis.llm.vertex.project-id");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.vertex.project-id is required when analysis.llm.provider=vertex");
    }

    @Test
    void vertexModeRequiresLocation() {
        Map<String, Object> values = vertexValues();
        values.remove("analysis.llm.vertex.location");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.vertex.location is required when analysis.llm.provider=vertex");
    }

    @Test
    void rejectsBlankModelName() {
        Map<String, Object> values = vertexValues();
        values.put("analysis.llm.model-name", "  ");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("analysis.llm.model-name must not be blank");
    }

    @Test
    void rejectsNegativeRequestDelay() {
        Map<String, Object> values = vertexValues();
        values.put("analysis.llm.request-delay", "-1s");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("analysis.llm.request-delay must not be negative");
    }

    @Test
    void gatewayModeDoesNotRequireVertexSettings() {
        Llm llm = bind(gatewayValues());

        assertThat(llm.provider()).isEqualTo(LlmProvider.GATEWAY);
        assertThat(llm.vertex().projectId()).isEmpty();
        assertThat(llm.vertex().location()).isEmpty();
        assertThat(llm.gateway().baseUrl())
                .isEqualTo("https://gateway.example.test/platform/google-vertex/proxy/v1beta");
        assertThat(llm.gateway().basicAuthToken()).isEqualTo(BASE64_TOKEN);
    }

    @Test
    void gatewayModeRequiresBaseUrl() {
        Map<String, Object> values = gatewayValues();
        values.remove("analysis.llm.gateway.base-url");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.gateway.base-url is required when analysis.llm.provider=gateway"
                        + " (full URL including the /v1beta suffix)");
    }

    @Test
    void gatewayModeRequiresBasicAuthToken() {
        Map<String, Object> values = gatewayValues();
        values.remove("analysis.llm.gateway.basic-auth-token");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage(
                        "analysis.llm.gateway.basic-auth-token is required when analysis.llm.provider=gateway");
    }

    @Test
    void gatewayModeRejectsNonBase64Token() {
        Map<String, Object> values = gatewayValues();
        values.put("analysis.llm.gateway.basic-auth-token", "not base64 !!");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.gateway.basic-auth-token must be a Base64-encoded credential");
    }

    @Test
    void gatewayModeStripsTrailingSlashes() {
        Map<String, Object> values = gatewayValues();
        values.put("analysis.llm.gateway.base-url", "https://gateway.example.test/proxy/v1beta/");

        assertThat(bind(values).gateway().baseUrl()).isEqualTo("https://gateway.example.test/proxy/v1beta");
    }

    @Test
    void gatewayModeRejectsHttpForNonLoopbackHosts() {
        Map<String, Object> values = gatewayValues();
        values.put("analysis.llm.gateway.base-url", "http://gateway.example.test/proxy/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.gateway.base-url must use HTTPS unless the host is loopback");
    }

    @Test
    void gatewayModeAllowsHttpLoopback() {
        Map<String, Object> values = gatewayValues();
        values.put("analysis.llm.gateway.base-url", "http://localhost:8080/proxy/v1beta");

        assertThat(bind(values).gateway().baseUrl()).isEqualTo("http://localhost:8080/proxy/v1beta");
    }

    @Test
    void gatewayModeRejectsQueryAndFragment() {
        Map<String, Object> values = gatewayValues();
        values.put("analysis.llm.gateway.base-url", "https://gateway.example.test/proxy/v1beta?debug=true");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.gateway.base-url must not contain a query or fragment");
    }

    @Test
    void gatewayModeRejectsNonPositiveTimeout() {
        Map<String, Object> values = gatewayValues();
        values.put("analysis.llm.gateway.timeout", "0s");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("analysis.llm.gateway.timeout must be positive");
    }

    @Test
    void rejectsUnknownProvider() {
        Map<String, Object> values = vertexValues();
        values.put("analysis.llm.provider", "azure");

        assertThatThrownBy(() -> bind(values)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void gatewayToStringNeverIncludesToken() {
        Llm llm = bind(gatewayValues());

        assertThat(llm.gateway().toString()).doesNotContain(BASE64_TOKEN).contains("basicAuthToken=<redacted>");
        assertThat(llm.toString()).doesNotContain(BASE64_TOKEN);
    }

    private static Llm bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("analysis.llm", Llm.class)
                .get();
    }

    private static Map<String, Object> vertexValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.llm.model-name", "gemini-2.5-flash");
        values.put("analysis.llm.vertex.project-id", "test-project");
        values.put("analysis.llm.vertex.location", "europe-west2");
        return values;
    }

    private static Map<String, Object> gatewayValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.llm.provider", "gateway");
        values.put("analysis.llm.model-name", "gemini-2.5-flash");
        values.put("analysis.llm.gateway.base-url", "https://gateway.example.test/platform/google-vertex/proxy/v1beta");
        values.put("analysis.llm.gateway.basic-auth-token", BASE64_TOKEN);
        return values;
    }
}
