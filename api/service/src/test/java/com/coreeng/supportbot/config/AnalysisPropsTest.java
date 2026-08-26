package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.config.AnalysisProps.Llm;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AnalysisPropsTest {

    private static final String BASE64_TOKEN = "dXNlcjpwYXNz";
    private static final String API_KEY = "AIzaSyTestKeyNotReal";
    private static final String EXACTLY_ONE_PROVIDER = "exactly one of analysis.llm.vertex.enabled,"
            + " analysis.llm.proxy.enabled and analysis.llm.google-ai.enabled must be true";

    @Test
    void defaultsToVertexWithoutRequiringProxySettings() {
        Llm llm = bind(vertexValues());

        assertThat(llm.vertex().enabled()).isTrue();
        assertThat(llm.proxy().enabled()).isFalse();
        assertThat(llm.modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(llm.requestDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(llm.vertex().projectId()).isEqualTo("test-project");
        assertThat(llm.vertex().location()).isEqualTo("europe-west2");
        assertThat(llm.proxy().baseUrl()).isEmpty();
        assertThat(llm.proxy().auth().basicAuthToken()).isEmpty();
        assertThat(llm.proxy().timeout()).isEqualTo(Duration.ofSeconds(20));
    }

    /** Every combination of the three provider flags; exactly one enabled is the only legal shape. */
    @ParameterizedTest(name = "vertex={0}, proxy={1}, googleAi={2} -> valid={3}")
    @CsvSource({
        "true,  false, false, true",
        "false, true,  false, true",
        "false, false, true,  true",
        "false, false, false, false",
        "true,  true,  false, false",
        "true,  false, true,  false",
        "false, true,  true,  false",
        "true,  true,  true,  false",
    })
    void acceptsExactlyOneEnabledProvider(boolean vertex, boolean proxy, boolean googleAi, boolean valid) {
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.prompt.enabled", "true");
        values.put("analysis.llm.model-name", "gemini-2.5-flash");
        // Each provider's own settings are supplied throughout, so the only thing under test is the
        // mutual-exclusion rule rather than an incidental missing-field failure.
        values.put("analysis.llm.vertex.enabled", Boolean.toString(vertex));
        values.put("analysis.llm.vertex.project-id", "test-project");
        values.put("analysis.llm.vertex.location", "europe-west2");
        values.put("analysis.llm.proxy.enabled", Boolean.toString(proxy));
        values.put("analysis.llm.proxy.base-url", "https://llm-proxy.example.test/proxy/v1beta");
        values.put("analysis.llm.proxy.auth.basic-auth-token", BASE64_TOKEN);
        values.put("analysis.llm.google-ai.enabled", Boolean.toString(googleAi));
        values.put("analysis.llm.google-ai.api-key", "test-api-key");

        if (valid) {
            assertThat(bind(values)).isNotNull();
        } else {
            assertThatThrownBy(() -> bind(values)).hasRootCauseMessage(EXACTLY_ONE_PROVIDER);
        }
    }

    @Test
    void rejectsBothProvidersEnabled() {
        Map<String, Object> values = vertexValues();
        values.put("analysis.llm.proxy.enabled", "true");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage(EXACTLY_ONE_PROVIDER);
    }

    @Test
    void rejectsNeitherProviderEnabled() {
        Map<String, Object> values = vertexValues();
        values.put("analysis.llm.vertex.enabled", "false");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage(EXACTLY_ONE_PROVIDER);
    }

    @Test
    void googleAiModeDoesNotRequireVertexOrProxySettings() {
        Llm llm = bind(googleAiValues());

        assertThat(llm.googleAi().enabled()).isTrue();
        assertThat(llm.vertex().enabled()).isFalse();
        assertThat(llm.proxy().enabled()).isFalse();
        assertThat(llm.googleAi().apiKey()).isEqualTo(API_KEY);
        assertThat(llm.vertex().projectId()).isEmpty();
        assertThat(llm.proxy().baseUrl()).isEmpty();
    }

    @Test
    void googleAiModeRequiresApiKey() {
        Map<String, Object> values = googleAiValues();
        values.remove("analysis.llm.google-ai.api-key");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage(
                        "analysis.llm.google-ai.api-key is required when analysis.llm.google-ai.enabled=true");
    }

    @Test
    void googleAiModeRejectsBlankApiKey() {
        Map<String, Object> values = googleAiValues();
        values.put("analysis.llm.google-ai.api-key", "   ");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage(
                        "analysis.llm.google-ai.api-key is required when analysis.llm.google-ai.enabled=true");
    }

    @Test
    void trimsGoogleAiApiKey() {
        Map<String, Object> values = googleAiValues();
        values.put("analysis.llm.google-ai.api-key", " " + API_KEY + " ");

        assertThat(bind(values).googleAi().apiKey()).isEqualTo(API_KEY);
    }

    @Test
    void googleAiToStringNeverIncludesApiKey() {
        Llm llm = bind(googleAiValues());

        assertThat(llm.googleAi().toString()).doesNotContain(API_KEY).contains("apiKey=<redacted>");
        assertThat(llm.toString()).doesNotContain(API_KEY);
    }

    @Test
    void vertexModeRequiresProjectId() {
        Map<String, Object> values = vertexValues();
        values.remove("analysis.llm.vertex.project-id");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage(
                        "analysis.llm.vertex.project-id is required when analysis.llm.vertex.enabled=true");
    }

    @Test
    void vertexModeRequiresLocation() {
        Map<String, Object> values = vertexValues();
        values.remove("analysis.llm.vertex.location");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.vertex.location is required when analysis.llm.vertex.enabled=true");
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
    void proxyModeDoesNotRequireVertexSettings() {
        Llm llm = bind(proxyValues());

        assertThat(llm.proxy().enabled()).isTrue();
        assertThat(llm.vertex().enabled()).isFalse();
        assertThat(llm.vertex().projectId()).isEmpty();
        assertThat(llm.vertex().location()).isEmpty();
        assertThat(llm.proxy().baseUrl())
                .isEqualTo("https://llm-proxy.example.test/platform/google-vertex/proxy/v1beta");
        assertThat(llm.proxy().auth().basicAuthToken()).isEqualTo(BASE64_TOKEN);
    }

    @Test
    void trimsBasicAuthToken() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.auth.basic-auth-token", " " + BASE64_TOKEN + " ");

        assertThat(bind(values).proxy().auth().basicAuthToken()).isEqualTo(BASE64_TOKEN);
    }

    @Test
    void proxyModeRequiresBaseUrl() {
        Map<String, Object> values = proxyValues();
        values.remove("analysis.llm.proxy.base-url");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.proxy.base-url is required when analysis.llm.proxy.enabled=true"
                        + " (full URL including the /v1beta suffix)");
    }

    @Test
    void proxyModeRequiresBasicAuthToken() {
        Map<String, Object> values = proxyValues();
        values.remove("analysis.llm.proxy.auth.basic-auth-token");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage(
                        "analysis.llm.proxy.auth.basic-auth-token is required when analysis.llm.proxy.enabled=true");
    }

    @Test
    void proxyModeRejectsNonBase64Token() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.auth.basic-auth-token", "not base64 !!");

        // The decoder's exception is chained as the cause, so the friendly message is mid-chain, not root.
        assertThatThrownBy(() -> bind(values))
                .hasStackTraceContaining(
                        "analysis.llm.proxy.auth.basic-auth-token must be a Base64-encoded credential");
    }

    @Test
    void proxyModeStripsTrailingSlashes() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "https://llm-proxy.example.test/proxy/v1beta/");

        assertThat(bind(values).proxy().baseUrl()).isEqualTo("https://llm-proxy.example.test/proxy/v1beta");
    }

    @Test
    void proxyModeAllowsHttp() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "http://llm-proxy.example.test/proxy/v1beta");

        assertThat(bind(values).proxy().baseUrl()).isEqualTo("http://llm-proxy.example.test/proxy/v1beta");
    }

    @Test
    void proxyModeRejectsQueryAndFragment() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "https://llm-proxy.example.test/proxy/v1beta?debug=true");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.proxy.base-url must not contain a query or fragment");
    }

    @Test
    void proxyModeRejectsNonHttpScheme() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "ftp://llm-proxy.example.test/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsRelativeUrl() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "llm-proxy.example.test/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsUserInfoInUrl() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "https://user:pass@llm-proxy.example.test/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("analysis.llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsUnparseableUrl() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.base-url", "https://llm-proxy.example.test/v1 beta");

        // The URISyntaxException is chained as the cause, so the friendly message is mid-chain, not root.
        assertThatThrownBy(() -> bind(values))
                .hasStackTraceContaining("analysis.llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsNonPositiveTimeout() {
        Map<String, Object> values = proxyValues();
        values.put("analysis.llm.proxy.timeout", "0s");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("analysis.llm.proxy.timeout must be positive");
    }

    @Test
    void proxyToStringNeverIncludesToken() {
        Llm llm = bind(proxyValues());

        assertThat(llm.proxy().auth().toString()).doesNotContain(BASE64_TOKEN).contains("basicAuthToken=<redacted>");
        assertThat(llm.proxy().toString()).doesNotContain(BASE64_TOKEN);
        assertThat(llm.toString()).doesNotContain(BASE64_TOKEN);
    }

    @Test
    void skipsLlmValidationWhenPromptDisabled() {
        // Neither provider enabled and no model name: invalid for the feature, but the feature is
        // off, so binding must not block startup.
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.prompt.enabled", "false");
        values.put("analysis.llm.vertex.enabled", "false");

        Llm llm = bind(values);

        assertThat(llm.vertex().enabled()).isFalse();
        assertThat(llm.proxy().enabled()).isFalse();
        assertThat(llm.googleAi().enabled()).isFalse();
        assertThat(llm.modelName()).isEmpty();
    }

    private static Llm bind(Map<String, Object> properties) {
        // Bound at the analysis root, not analysis.llm: validation runs in the AnalysisProps
        // constructor because it is gated on analysis.prompt.enabled.
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("analysis", AnalysisProps.class)
                .get()
                .llm();
    }

    private static Map<String, Object> vertexValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.prompt.enabled", "true");
        values.put("analysis.llm.model-name", "gemini-2.5-flash");
        values.put("analysis.llm.vertex.project-id", "test-project");
        values.put("analysis.llm.vertex.location", "europe-west2");
        return values;
    }

    private static Map<String, Object> googleAiValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.prompt.enabled", "true");
        values.put("analysis.llm.vertex.enabled", "false");
        values.put("analysis.llm.google-ai.enabled", "true");
        values.put("analysis.llm.model-name", "gemini-2.5-flash");
        values.put("analysis.llm.google-ai.api-key", API_KEY);
        return values;
    }

    private static Map<String, Object> proxyValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("analysis.prompt.enabled", "true");
        values.put("analysis.llm.vertex.enabled", "false");
        values.put("analysis.llm.proxy.enabled", "true");
        values.put("analysis.llm.model-name", "gemini-2.5-flash");
        values.put("analysis.llm.proxy.base-url", "https://llm-proxy.example.test/platform/google-vertex/proxy/v1beta");
        values.put("analysis.llm.proxy.auth.basic-auth-token", BASE64_TOKEN);
        return values;
    }
}
