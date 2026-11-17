package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class LlmPropsTest {

    private static final String BASE64_TOKEN = "dXNlcjpwYXNz";
    private static final String ANTHROPIC_KEY = "test-anthropic-key";
    private static final String STUB_GUARD = "llm.provider=stub also requires llm.stub.acknowledge-synthetic-data=true:"
            + " the stub LLM provider writes synthetic rows into analysis and summary_snapshot,"
            + " is for local development only and must never be enabled against a shared database";

    @Test
    void defaultsToNoProviderWithNothingValidated() {
        // The feature-off shape: no provider, no model name, no credentials — and no complaint.
        LlmProps props = bind(new HashMap<>());

        assertThat(props.provider()).isEqualTo(LlmProvider.NONE);
        assertThat(props.enabled()).isFalse();
        assertThat(props.modelName()).isEmpty();
        assertThat(props.requestDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(props.vertex().projectId()).isEmpty();
        assertThat(props.proxy().baseUrl()).isEmpty();
        assertThat(props.proxy().timeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(props.anthropic().apiKey()).isEmpty();
        assertThat(props.anthropic().baseUrl()).isEmpty();
        assertThat(props.anthropic().timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.anthropic().maxTokens()).isEqualTo(16000);
        assertThat(props.stub().acknowledgeSyntheticData()).isFalse();
    }

    @Test
    void explicitNoneIsTheSameAsUnset() {
        Map<String, Object> values = new HashMap<>();
        values.put("llm.provider", "none");

        assertThat(bind(values).enabled()).isFalse();
    }

    @ParameterizedTest(name = "llm.provider={0}")
    @ValueSource(strings = {"vertex", "VERTEX", "Vertex"})
    void bindsTheProviderCaseInsensitively(String provider) {
        // Environment variables tend to arrive upper-cased; the switch must not care.
        Map<String, Object> values = vertexValues();
        values.put("llm.provider", provider);

        assertThat(bind(values).provider()).isEqualTo(LlmProvider.VERTEX);
    }

    @Test
    void rejectsAnUnknownProvider() {
        Map<String, Object> values = vertexValues();
        values.put("llm.provider", "openai");

        assertThatThrownBy(() -> bind(values)).hasStackTraceContaining("openai");
    }

    @Test
    void vertexModeDoesNotRequireProxyOrStubSettings() {
        LlmProps props = bind(vertexValues());

        assertThat(props.provider()).isEqualTo(LlmProvider.VERTEX);
        assertThat(props.enabled()).isTrue();
        assertThat(props.modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(props.vertex().projectId()).isEqualTo("test-project");
        assertThat(props.vertex().location()).isEqualTo("europe-west2");
        assertThat(props.proxy().baseUrl()).isEmpty();
        assertThat(props.proxy().auth().basicAuthToken()).isEmpty();
    }

    @Test
    void vertexModeRequiresProjectId() {
        Map<String, Object> values = vertexValues();
        values.remove("llm.vertex.project-id");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.vertex.project-id is required when llm.provider=vertex");
    }

    @Test
    void vertexModeRequiresLocation() {
        Map<String, Object> values = vertexValues();
        values.remove("llm.vertex.location");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.vertex.location is required when llm.provider=vertex");
    }

    @Test
    void geminiProvidersDefaultTheModelName() {
        // application.yaml leaves llm.model-name empty; the Gemini providers keep their old default.
        for (Map<String, Object> values : List.of(vertexValues(), proxyValues(), stubValues())) {
            values.remove("llm.model-name");

            assertThat(bind(values).modelName()).isEqualTo("gemini-2.5-flash");
        }
    }

    @Test
    void blankModelNameFallsBackToTheGeminiDefault() {
        Map<String, Object> values = vertexValues();
        values.put("llm.model-name", "  ");

        assertThat(bind(values).modelName()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void rejectsNegativeRequestDelay() {
        Map<String, Object> values = vertexValues();
        values.put("llm.request-delay", "-1s");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("llm.request-delay must not be negative");
    }

    @Test
    void stubModeNeedsNoCredentialsAtAll() {
        // The point of the mode: no project, no base URL, no token — just the provider and the
        // acknowledgement.
        LlmProps props = bind(stubValues());

        assertThat(props.provider()).isEqualTo(LlmProvider.STUB);
        assertThat(props.stub().acknowledgeSyntheticData()).isTrue();
        assertThat(props.vertex().projectId()).isEmpty();
        assertThat(props.proxy().baseUrl()).isEmpty();
    }

    @Test
    void stubModeRequiresSyntheticDataAcknowledgement() {
        // The hard guard: the provider on its own must not start the app, or a copied-over local
        // LLM_PROVIDER=stub could write synthetic rows into a shared database.
        Map<String, Object> values = stubValues();
        values.remove("llm.stub.acknowledge-synthetic-data");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage(STUB_GUARD);
    }

    @Test
    void stubModeRejectsExplicitlyUnacknowledgedSyntheticData() {
        Map<String, Object> values = stubValues();
        values.put("llm.stub.acknowledge-synthetic-data", "false");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage(STUB_GUARD);
    }

    @Test
    void acknowledgementAloneDoesNotSelectTheStub() {
        // The acknowledgement is a consent flag, not a provider switch.
        Map<String, Object> values = vertexValues();
        values.put("llm.stub.acknowledge-synthetic-data", "true");

        assertThat(bind(values).provider()).isEqualTo(LlmProvider.VERTEX);
    }

    @Test
    void stubGuardIsSkippedWhenNoProviderIsSelected() {
        // Same rule as every other LLM setting: with the feature off, nothing here can block startup.
        Map<String, Object> values = new HashMap<>();
        values.put("llm.stub.acknowledge-synthetic-data", "false");

        assertThat(bind(values).enabled()).isFalse();
    }

    @Test
    void proxyModeDoesNotRequireVertexSettings() {
        LlmProps props = bind(proxyValues());

        assertThat(props.provider()).isEqualTo(LlmProvider.PROXY);
        assertThat(props.vertex().projectId()).isEmpty();
        assertThat(props.vertex().location()).isEmpty();
        assertThat(props.proxy().baseUrl())
                .isEqualTo("https://llm-proxy.example.test/platform/google-vertex/proxy/v1beta");
        assertThat(props.proxy().auth().basicAuthToken()).isEqualTo(BASE64_TOKEN);
    }

    @Test
    void trimsBasicAuthToken() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.auth.basic-auth-token", " " + BASE64_TOKEN + " ");

        assertThat(bind(values).proxy().auth().basicAuthToken()).isEqualTo(BASE64_TOKEN);
    }

    @Test
    void proxyModeRequiresBaseUrl() {
        Map<String, Object> values = proxyValues();
        values.remove("llm.proxy.base-url");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.proxy.base-url is required when llm.provider=proxy"
                        + " (full URL including the /v1beta suffix)");
    }

    @Test
    void proxyModeRequiresBasicAuthToken() {
        Map<String, Object> values = proxyValues();
        values.remove("llm.proxy.auth.basic-auth-token");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.proxy.auth.basic-auth-token is required when llm.provider=proxy");
    }

    @Test
    void proxyModeRejectsNonBase64Token() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.auth.basic-auth-token", "not base64 !!");

        // The decoder's exception is chained as the cause, so the friendly message is mid-chain, not root.
        assertThatThrownBy(() -> bind(values))
                .hasStackTraceContaining("llm.proxy.auth.basic-auth-token must be a Base64-encoded credential");
    }

    @Test
    void proxyModeStripsTrailingSlashes() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "https://llm-proxy.example.test/proxy/v1beta/");

        assertThat(bind(values).proxy().baseUrl()).isEqualTo("https://llm-proxy.example.test/proxy/v1beta");
    }

    @Test
    void proxyModeAllowsHttp() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "http://llm-proxy.example.test/proxy/v1beta");

        assertThat(bind(values).proxy().baseUrl()).isEqualTo("http://llm-proxy.example.test/proxy/v1beta");
    }

    @Test
    void proxyModeRejectsQueryAndFragment() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "https://llm-proxy.example.test/proxy/v1beta?debug=true");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.proxy.base-url must not contain a query or fragment");
    }

    @Test
    void proxyModeRejectsNonHttpScheme() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "ftp://llm-proxy.example.test/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsRelativeUrl() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "llm-proxy.example.test/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsUserInfoInUrl() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "https://user:pass@llm-proxy.example.test/v1beta");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsUnparseableUrl() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.base-url", "https://llm-proxy.example.test/v1 beta");

        // The URISyntaxException is chained as the cause, so the friendly message is mid-chain, not root.
        assertThatThrownBy(() -> bind(values))
                .hasStackTraceContaining("llm.proxy.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void proxyModeRejectsNonPositiveTimeout() {
        Map<String, Object> values = proxyValues();
        values.put("llm.proxy.timeout", "0s");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("llm.proxy.timeout must be positive");
    }

    @Test
    void proxyToStringNeverIncludesToken() {
        LlmProps props = bind(proxyValues());

        assertThat(props.proxy().auth().toString()).doesNotContain(BASE64_TOKEN).contains("basicAuthToken=<redacted>");
        assertThat(props.proxy().toString()).doesNotContain(BASE64_TOKEN);
        assertThat(props.toString()).doesNotContain(BASE64_TOKEN);
    }

    @Test
    void anthropicModeDoesNotRequireVertexOrProxySettings() {
        LlmProps props = bind(anthropicValues());

        assertThat(props.provider()).isEqualTo(LlmProvider.ANTHROPIC);
        assertThat(props.enabled()).isTrue();
        assertThat(props.modelName()).isEqualTo("claude-opus-5-5");
        assertThat(props.anthropic().apiKey()).isEqualTo(ANTHROPIC_KEY);
        assertThat(props.anthropic().baseUrl()).isEmpty();
        assertThat(props.anthropic().timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.anthropic().maxTokens()).isEqualTo(16000);
        assertThat(props.vertex().projectId()).isEmpty();
        assertThat(props.proxy().baseUrl()).isEmpty();
    }

    @Test
    void anthropicModeRequiresAnExplicitModelName() {
        // No Gemini default here: it would only fail at the first call, so startup must fail instead.
        Map<String, Object> values = anthropicValues();
        values.remove("llm.model-name");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.model-name is required when llm.provider=anthropic");
    }

    @Test
    void anthropicModeRejectsBlankModelName() {
        Map<String, Object> values = anthropicValues();
        values.put("llm.model-name", "  ");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.model-name is required when llm.provider=anthropic");
    }

    @Test
    void anthropicModeRequiresApiKey() {
        Map<String, Object> values = anthropicValues();
        values.remove("llm.anthropic.api-key");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.anthropic.api-key is required when llm.provider=anthropic");
    }

    @Test
    void trimsAnthropicApiKey() {
        Map<String, Object> values = anthropicValues();
        values.put("llm.anthropic.api-key", " " + ANTHROPIC_KEY + " ");

        assertThat(bind(values).anthropic().apiKey()).isEqualTo(ANTHROPIC_KEY);
    }

    @Test
    void anthropicModeAcceptsCustomBaseUrl() {
        Map<String, Object> values = anthropicValues();
        values.put("llm.anthropic.base-url", "https://llm-gateway.example.test/anthropic/v1");

        assertThat(bind(values).anthropic().baseUrl()).isEqualTo("https://llm-gateway.example.test/anthropic/v1");
    }

    @ParameterizedTest(name = "llm.anthropic.base-url={0}")
    @ValueSource(
            strings = {
                "ftp://llm-gateway.example.test/v1",
                "llm-gateway.example.test/v1",
                "https://user:pass@llm-gateway.example.test/v1"
            })
    void anthropicModeRejectsNonHttpOrRelativeBaseUrl(String baseUrl) {
        Map<String, Object> values = anthropicValues();
        values.put("llm.anthropic.base-url", baseUrl);

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.anthropic.base-url must be an absolute HTTP(S) URL");
    }

    @Test
    void anthropicModeRejectsQueryInBaseUrl() {
        Map<String, Object> values = anthropicValues();
        values.put("llm.anthropic.base-url", "https://llm-gateway.example.test/v1?debug=true");

        assertThatThrownBy(() -> bind(values))
                .hasRootCauseMessage("llm.anthropic.base-url must not contain a query or fragment");
    }

    @ParameterizedTest(name = "llm.anthropic.timeout={0}")
    @ValueSource(strings = {"0s", "-1s"})
    void anthropicModeRejectsNonPositiveTimeout(String timeout) {
        Map<String, Object> values = anthropicValues();
        values.put("llm.anthropic.timeout", timeout);

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("llm.anthropic.timeout must be positive");
    }

    @ParameterizedTest(name = "llm.anthropic.max-tokens={0}")
    @ValueSource(strings = {"0", "-1"})
    void anthropicModeRejectsNonPositiveMaxTokens(String maxTokens) {
        Map<String, Object> values = anthropicValues();
        values.put("llm.anthropic.max-tokens", maxTokens);

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("llm.anthropic.max-tokens must be positive");
    }

    @Test
    void anthropicToStringNeverIncludesApiKey() {
        LlmProps props = bind(anthropicValues());

        assertThat(props.anthropic().toString()).doesNotContain(ANTHROPIC_KEY).contains("apiKey=<redacted>");
        assertThat(props.toString()).doesNotContain(ANTHROPIC_KEY);
    }

    @Test
    void anthropicSettingsAreNotValidatedForOtherProviders() {
        // Junk anthropic settings must not block a vertex or proxy deployment.
        for (Map<String, Object> values : List.of(vertexValues(), proxyValues())) {
            values.put("llm.anthropic.base-url", "not a url");
            values.put("llm.anthropic.timeout", "0s");
            values.put("llm.anthropic.max-tokens", "0");

            assertThat(bind(values).enabled()).isTrue();
        }
    }

    @Test
    void skipsProviderValidationWhenNoProviderIsSelected() {
        // Invalid for any provider (no model name, negative delay, junk proxy URL), but no provider
        // is selected, so binding must not block startup.
        Map<String, Object> values = new HashMap<>();
        values.put("llm.request-delay", "-1s");
        values.put("llm.proxy.base-url", "not a url");

        LlmProps props = bind(values);

        assertThat(props.enabled()).isFalse();
        assertThat(props.modelName()).isEmpty();
    }

    private static LlmProps bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("llm", LlmProps.class)
                .orElseGet(() -> new Binder(new MapConfigurationPropertySource(Map.of("llm.provider", "none")))
                        .bind("llm", LlmProps.class)
                        .get());
    }

    private static Map<String, Object> vertexValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("llm.provider", "vertex");
        values.put("llm.model-name", "gemini-2.5-flash");
        values.put("llm.vertex.project-id", "test-project");
        values.put("llm.vertex.location", "europe-west2");
        return values;
    }

    private static Map<String, Object> stubValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("llm.provider", "stub");
        values.put("llm.model-name", "stub-local");
        values.put("llm.stub.acknowledge-synthetic-data", "true");
        return values;
    }

    private static Map<String, Object> anthropicValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("llm.provider", "anthropic");
        values.put("llm.model-name", "claude-opus-5-5");
        values.put("llm.anthropic.api-key", ANTHROPIC_KEY);
        return values;
    }

    private static Map<String, Object> proxyValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("llm.provider", "proxy");
        values.put("llm.model-name", "gemini-2.5-flash");
        values.put("llm.proxy.base-url", "https://llm-proxy.example.test/platform/google-vertex/proxy/v1beta");
        values.put("llm.proxy.auth.basic-auth-token", BASE64_TOKEN);
        return values;
    }
}
