package com.coreeng.supportbot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * LLM configuration shared by the analysis run and the Support Summary page.
 *
 * <p>{@link #provider()} is the only feature flag: {@link LlmProvider#NONE} (the default) switches
 * both features off, anything else switches them on and names the client to build. Only the
 * selected provider's settings are validated, so a deployment with the feature off is never blocked
 * from starting by provider config it does not use.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProps(
        @DefaultValue("none") LlmProvider provider,
        @DefaultValue("") String modelName,
        @DefaultValue("500ms") Duration requestDelay,
        @DefaultValue Vertex vertex,
        @DefaultValue Proxy proxy,
        @DefaultValue Anthropic anthropic,
        @DefaultValue Stub stub) {

    static final String DEFAULT_GEMINI_MODEL_NAME = "gemini-2.5-flash";

    public LlmProps {
        modelName = modelName.trim();
        // No default for anthropic: a Gemini name would only fail at the first call, not at startup.
        if (modelName.isEmpty()) {
            modelName = switch (provider) {
                case VERTEX, PROXY, STUB -> DEFAULT_GEMINI_MODEL_NAME;
                case NONE, ANTHROPIC -> "";
            };
        }
        if (provider != LlmProvider.NONE) {
            if (modelName.isEmpty()) {
                throw new IllegalArgumentException("llm.model-name is required when llm.provider="
                        + provider.name().toLowerCase(Locale.ROOT));
            }
            if (requestDelay.isNegative()) {
                throw new IllegalArgumentException("llm.request-delay must not be negative");
            }
            switch (provider) {
                case VERTEX -> vertex.validate();
                case PROXY -> proxy.validate();
                case ANTHROPIC -> anthropic.validate();
                case STUB -> stub.validate();
                case NONE -> throw new AssertionError("unreachable");
            }
        }
    }

    /** Whether the LLM-backed features (analysis run, Support Summary page) are on. */
    public boolean enabled() {
        return provider != LlmProvider.NONE;
    }

    public record Vertex(
            @DefaultValue("") String projectId,
            @DefaultValue("") String location) {

        public Vertex {
            projectId = projectId.trim();
            location = location.trim();
        }

        void validate() {
            if (projectId.isEmpty()) {
                throw new IllegalArgumentException("llm.vertex.project-id is required when llm.provider=vertex");
            }
            if (location.isEmpty()) {
                throw new IllegalArgumentException("llm.vertex.location is required when llm.provider=vertex");
            }
        }
    }

    public record Proxy(
            @DefaultValue("") String baseUrl,
            @DefaultValue Auth auth,
            @DefaultValue("20s") Duration timeout) {

        public Proxy {
            baseUrl = stripTrailingSlashes(baseUrl.trim());
        }

        void validate() {
            if (baseUrl.isEmpty()) {
                throw new IllegalArgumentException("llm.proxy.base-url is required when llm.provider=proxy"
                        + " (full URL including the /v1beta suffix)");
            }
            validateBaseUrl("llm.proxy.base-url", baseUrl);
            auth.validate();
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("llm.proxy.timeout must be positive");
            }
        }

        private static String stripTrailingSlashes(String value) {
            String normalized = value;
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        public record Auth(@DefaultValue("") String basicAuthToken) {

            public Auth {
                basicAuthToken = basicAuthToken.trim();
            }

            void validate() {
                if (basicAuthToken.isEmpty()) {
                    throw new IllegalArgumentException(
                            "llm.proxy.auth.basic-auth-token is required when llm.provider=proxy");
                }
                try {
                    Base64.getDecoder().decode(basicAuthToken);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "llm.proxy.auth.basic-auth-token must be a Base64-encoded credential", e);
                }
            }

            @Override
            public String toString() {
                return "Auth[basicAuthToken=<redacted>]";
            }
        }
    }

    public record Anthropic(
            @DefaultValue("") String apiKey,
            @DefaultValue("") String baseUrl,
            @DefaultValue("60s") Duration timeout,
            @DefaultValue("16000") int maxTokens) {

        public Anthropic {
            apiKey = apiKey.trim();
            baseUrl = baseUrl.trim();
        }

        void validate() {
            if (apiKey.isEmpty()) {
                throw new IllegalArgumentException("llm.anthropic.api-key is required when llm.provider=anthropic");
            }
            if (!baseUrl.isEmpty()) {
                validateBaseUrl("llm.anthropic.base-url", baseUrl);
            }
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("llm.anthropic.timeout must be positive");
            }
            if (maxTokens <= 0) {
                throw new IllegalArgumentException("llm.anthropic.max-tokens must be positive");
            }
        }

        @Override
        public String toString() {
            return "Anthropic[apiKey=<redacted>, baseUrl=" + baseUrl + ", timeout=" + timeout + ", maxTokens="
                    + maxTokens + "]";
        }
    }

    private static void validateBaseUrl(String propertyName, String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL", e);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || scheme == null
                || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                        || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
            throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(propertyName + " must not contain a query or fragment");
        }
    }

    /**
     * Local development and demo provider: canned, deterministic responses and no network calls at
     * all. Takes no credentials, which is the point — neither GCP IAM nor the internal proxy's token
     * is needed to exercise the analysis and Support Summary features end to end.
     *
     * <p>Local-only. Its output is stored exactly like real model output, so selecting it is a
     * two-step opt-in: {@code llm.provider=stub} picks the provider and
     * {@code acknowledgeSyntheticData} confirms the operator knows the database will receive
     * synthetic rows. Startup fails with the provider alone, so a copied-over
     * {@code LLM_PROVIDER=stub} cannot quietly reach a shared environment.
     */
    public record Stub(@DefaultValue("false") boolean acknowledgeSyntheticData) {

        static final String SYNTHETIC_DATA_GUARD =
                "llm.provider=stub also requires llm.stub.acknowledge-synthetic-data=true:"
                        + " the stub LLM provider writes synthetic rows into analysis and summary_snapshot,"
                        + " is for local development only and must never be enabled against a shared database";

        void validate() {
            if (!acknowledgeSyntheticData) {
                throw new IllegalArgumentException(SYNTHETIC_DATA_GUARD);
            }
        }
    }
}
