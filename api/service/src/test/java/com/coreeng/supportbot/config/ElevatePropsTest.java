package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ElevatePropsTest {

    @Test
    void allBlankConnectionValuesDisableElevateAndApplyDefaults() {
        ElevateProps props = bind(Map.of("elevate.base-url", ""));

        assertThat(props.configured()).isFalse();
        assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.maxServerRetryDelay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(props.statusInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(props.syncInterval()).isEqualTo(Duration.ofHours(12));
        assertThat(props.agentName()).isEqualTo("Support Bot");
        assertThat(props.supportBotUrl()).isEqualTo("http://localhost:3000");
        assertThat(props.version()).isEqualTo("dev");
    }

    @Test
    void bindsCompleteConnectionAndNormalizesTrailingSlash() {
        Map<String, Object> values = connectionValues();
        values.put("elevate.base-url", "https://elevate.example.test/");
        values.put("elevate.connect-timeout", "750ms");
        values.put("elevate.read-timeout", "45s");
        values.put("elevate.max-server-retry-delay", "90s");

        ElevateProps props = bind(values);

        assertThat(props.configured()).isTrue();
        assertThat(props.baseUrl()).isEqualTo("https://elevate.example.test");
        assertThat(props.clientId()).isEqualTo("esc_client");
        assertThat(props.clientSecret()).isEqualTo("secret-value");
        assertThat(props.connectTimeout()).isEqualTo(Duration.ofMillis(750));
        assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(props.maxServerRetryDelay()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void partialConnectionFailsFast() {
        assertThatThrownBy(() -> bind(
                        Map.of("elevate.base-url", "https://elevate.example.test", "elevate.client-id", "esc_client")))
                .hasRootCauseMessage(
                        "elevate.base-url, elevate.client-id, and elevate.client-secret must either all be configured or all be blank");
    }

    @Test
    void rejectsNonHttpAndRelativeUrls() {
        Map<String, Object> invalidBaseUrl = connectionValues();
        invalidBaseUrl.put("elevate.base-url", "file:///tmp/elevate");
        assertThatThrownBy(() -> bind(invalidBaseUrl))
                .hasRootCauseMessage("elevate.base-url must be an absolute HTTP(S) URL");

        Map<String, Object> invalidAgentUrl = connectionValues();
        invalidAgentUrl.put("elevate.support-bot-url", "/support");
        assertThatThrownBy(() -> bind(invalidAgentUrl))
                .hasRootCauseMessage("elevate.support-bot-url must be an absolute HTTP(S) URL");
    }

    @Test
    void rejectsBaseUrlCredentialsQueryAndFragment() {
        Map<String, Object> userInfo = connectionValues();
        userInfo.put("elevate.base-url", "https://test-user@elevate.example.test");
        assertThatThrownBy(() -> bind(userInfo))
                .hasRootCauseMessage("elevate.base-url must be an absolute HTTP(S) URL");

        Map<String, Object> query = connectionValues();
        query.put("elevate.base-url", "https://elevate.example.test?apiKey=secret");
        assertThatThrownBy(() -> bind(query))
                .hasRootCauseMessage("elevate.base-url must not contain a query or fragment");

        Map<String, Object> fragment = connectionValues();
        fragment.put("elevate.base-url", "https://elevate.example.test#internal");
        assertThatThrownBy(() -> bind(fragment))
                .hasRootCauseMessage("elevate.base-url must not contain a query or fragment");
    }

    @Test
    void rejectsPlainHttpForNonLoopbackElevateServers() {
        for (String baseUrl : List.of("http://elevate.example.test", "http://127.example.test")) {
            Map<String, Object> values = connectionValues();
            values.put("elevate.base-url", baseUrl);

            assertThatThrownBy(() -> bind(values))
                    .hasRootCauseMessage("elevate.base-url must use HTTPS unless the host is loopback");
        }
    }

    @Test
    void permitsPlainHttpForLoopbackDevelopmentServers() {
        for (String baseUrl : List.of("http://localhost:8099", "http://127.0.0.2:8099", "http://[::1]:8099")) {
            Map<String, Object> values = connectionValues();
            values.put("elevate.base-url", baseUrl);

            assertThat(bind(values).baseUrl()).isEqualTo(baseUrl);
        }
    }

    @Test
    void rejectsNonPositiveIntervals() {
        Map<String, Object> values = connectionValues();
        values.put("elevate.status-interval", "0s");

        assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("elevate.status-interval must be positive");
    }

    @Test
    void rejectsNonPositiveHttpTimeouts() {
        for (String property : List.of("connect-timeout", "read-timeout", "max-server-retry-delay")) {
            Map<String, Object> values = connectionValues();
            values.put("elevate." + property, "0s");

            assertThatThrownBy(() -> bind(values)).hasRootCauseMessage("elevate." + property + " must be positive");
        }
    }

    @Test
    void toStringNeverIncludesCredentials() {
        ElevateProps props = bind(connectionValues());

        assertThat(props.toString())
                .doesNotContain("esc_client", "secret-value")
                .contains("clientId=<redacted>", "clientSecret=<redacted>");
    }

    private static ElevateProps bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("elevate", ElevateProps.class)
                .get();
    }

    private static Map<String, Object> connectionValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("elevate.base-url", "https://elevate.example.test");
        values.put("elevate.client-id", "esc_client");
        values.put("elevate.client-secret", "secret-value");
        return values;
    }
}
