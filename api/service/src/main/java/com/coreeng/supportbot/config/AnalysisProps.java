package com.coreeng.supportbot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "analysis")
public record AnalysisProps(Llm llm, Bundle bundle, Prompt prompt) {

    public enum LlmProvider {
        VERTEX,
        GATEWAY
    }

    public record Llm(
            @DefaultValue("vertex") LlmProvider provider,
            @DefaultValue("") String modelName,
            @DefaultValue("500ms") Duration requestDelay,
            @DefaultValue Vertex vertex,
            @DefaultValue Gateway gateway) {

        public Llm {
            modelName = modelName.trim();
            if (modelName.isEmpty()) {
                throw new IllegalArgumentException("analysis.llm.model-name must not be blank");
            }
            if (requestDelay.isNegative()) {
                throw new IllegalArgumentException("analysis.llm.request-delay must not be negative");
            }
            // Only the selected provider's settings are required; the other side may stay blank.
            switch (provider) {
                case VERTEX -> vertex.validate();
                case GATEWAY -> gateway.validate();
            }
        }
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
                throw new IllegalArgumentException(
                        "analysis.llm.vertex.project-id is required when analysis.llm.provider=vertex");
            }
            if (location.isEmpty()) {
                throw new IllegalArgumentException(
                        "analysis.llm.vertex.location is required when analysis.llm.provider=vertex");
            }
        }
    }

    public record Gateway(
            @DefaultValue Proxy proxy,
            @DefaultValue Auth auth,
            @DefaultValue("30s") Duration timeout) {

        void validate() {
            proxy.validate();
            auth.validate();
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("analysis.llm.gateway.timeout must be positive");
            }
        }

        public record Proxy(@DefaultValue GoogleVertex googleVertex) {

            void validate() {
                googleVertex.validate();
            }
        }

        public record GoogleVertex(@DefaultValue("") String baseUrl) {

            public GoogleVertex {
                baseUrl = stripTrailingSlashes(baseUrl.trim());
            }

            void validate() {
                if (baseUrl.isEmpty()) {
                    throw new IllegalArgumentException(
                            "analysis.llm.gateway.proxy.google-vertex.base-url is required when"
                                    + " analysis.llm.provider=gateway (full URL including the /v1beta suffix)");
                }
                URI baseUri = validateHttpUrl("analysis.llm.gateway.proxy.google-vertex.base-url", baseUrl);
                if (baseUri.getQuery() != null || baseUri.getFragment() != null) {
                    throw new IllegalArgumentException(
                            "analysis.llm.gateway.proxy.google-vertex.base-url must not contain a query or fragment");
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
        }

        public record Auth(@DefaultValue("") String basicAuthToken) {

            public Auth {
                basicAuthToken = basicAuthToken.trim();
            }

            void validate() {
                if (basicAuthToken.isEmpty()) {
                    throw new IllegalArgumentException(
                            "analysis.llm.gateway.auth.basic-auth-token is required when analysis.llm.provider=gateway");
                }
                try {
                    Base64.getDecoder().decode(basicAuthToken);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "analysis.llm.gateway.auth.basic-auth-token must be a Base64-encoded credential", e);
                }
            }

            @Override
            public String toString() {
                return "Auth[basicAuthToken=<redacted>]";
            }
        }
    }

    public record Bundle(String path) {}

    public record Prompt(boolean enabled) {}
}
