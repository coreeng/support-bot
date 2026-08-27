package com.coreeng.supportbot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "analysis")
public record AnalysisProps(Llm llm, Bundle bundle, Prompt prompt) {

    public AnalysisProps {
        // LLM settings are only consulted when the analysis feature is on; a deployment with the
        // feature off must not be blocked from starting by provider config it never uses.
        if (prompt.enabled()) {
            llm.validate();
        }
    }

    public record Llm(
            @DefaultValue("") String modelName,
            @DefaultValue("500ms") Duration requestDelay,
            @DefaultValue Vertex vertex,
            @DefaultValue Proxy proxy,
            @DefaultValue Stub stub) {

        public Llm {
            modelName = modelName.trim();
        }

        void validate() {
            if (modelName.isEmpty()) {
                throw new IllegalArgumentException("analysis.llm.model-name must not be blank");
            }
            if (requestDelay.isNegative()) {
                throw new IllegalArgumentException("analysis.llm.request-delay must not be negative");
            }
            // Only the enabled provider's settings are required; the others may stay blank. Note that
            // vertex defaults to enabled, so selecting another provider means turning vertex off
            // explicitly — the same step proxy mode has always needed.
            long enabledProviders = Stream.of(vertex.enabled(), proxy.enabled(), stub.enabled())
                    .filter(Boolean::booleanValue)
                    .count();
            if (enabledProviders != 1) {
                throw new IllegalArgumentException("exactly one of analysis.llm.vertex.enabled,"
                        + " analysis.llm.proxy.enabled and analysis.llm.stub.enabled must be true");
            }
            if (vertex.enabled()) {
                vertex.validate();
            } else if (proxy.enabled()) {
                proxy.validate();
            }
            // The stub has nothing to validate: it takes no credentials and reaches no network.
        }
    }

    public record Vertex(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("") String projectId,
            @DefaultValue("") String location) {

        public Vertex {
            projectId = projectId.trim();
            location = location.trim();
        }

        void validate() {
            if (projectId.isEmpty()) {
                throw new IllegalArgumentException(
                        "analysis.llm.vertex.project-id is required when analysis.llm.vertex.enabled=true");
            }
            if (location.isEmpty()) {
                throw new IllegalArgumentException(
                        "analysis.llm.vertex.location is required when analysis.llm.vertex.enabled=true");
            }
        }
    }

    public record Proxy(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String baseUrl,
            @DefaultValue Auth auth,
            @DefaultValue("20s") Duration timeout) {

        public Proxy {
            baseUrl = stripTrailingSlashes(baseUrl.trim());
        }

        void validate() {
            if (baseUrl.isEmpty()) {
                throw new IllegalArgumentException(
                        "analysis.llm.proxy.base-url is required when analysis.llm.proxy.enabled=true"
                                + " (full URL including the /v1beta suffix)");
            }
            URI baseUri = validateHttpUrl("analysis.llm.proxy.base-url", baseUrl);
            if (baseUri.getQuery() != null || baseUri.getFragment() != null) {
                throw new IllegalArgumentException("analysis.llm.proxy.base-url must not contain a query or fragment");
            }
            auth.validate();
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("analysis.llm.proxy.timeout must be positive");
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
                            "analysis.llm.proxy.auth.basic-auth-token is required when analysis.llm.proxy.enabled=true");
                }
                try {
                    Base64.getDecoder().decode(basicAuthToken);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "analysis.llm.proxy.auth.basic-auth-token must be a Base64-encoded credential", e);
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
     * <p>May be dropped before merge — see {@code docs/plans/support-summary.md}.
     */
    public record Stub(@DefaultValue("false") boolean enabled) {}

    public record Bundle(String path) {}

    public record Prompt(boolean enabled) {}
}
