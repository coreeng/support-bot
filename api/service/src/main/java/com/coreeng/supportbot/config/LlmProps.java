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
        @DefaultValue Stub stub) {

    public LlmProps {
        modelName = modelName.trim();
        if (provider != LlmProvider.NONE) {
            if (modelName.isEmpty()) {
                throw new IllegalArgumentException("llm.model-name must not be blank");
            }
            if (requestDelay.isNegative()) {
                throw new IllegalArgumentException("llm.request-delay must not be negative");
            }
            switch (provider) {
                case VERTEX -> vertex.validate();
                case PROXY -> proxy.validate();
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
            URI baseUri = validateHttpUrl("llm.proxy.base-url", baseUrl);
            if (baseUri.getQuery() != null || baseUri.getFragment() != null) {
                throw new IllegalArgumentException("llm.proxy.base-url must not contain a query or fragment");
            }
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

        private static URI validateHttpUrl(String propertyName, String value) {
            try {
                URI uri = new URI(value);
                String scheme = uri.getScheme();
                if (!uri.isAbsolute()
                        || uri.getHost() == null
                        || uri.getUserInfo() != null
                        || scheme == null
                        || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                                || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
                    throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL");
                }
                return uri;
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL", e);
            }
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
