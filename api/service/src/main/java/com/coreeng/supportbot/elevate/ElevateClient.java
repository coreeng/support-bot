package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public final class ElevateClient {
    private static final int PAGE_LIMIT = 500;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MILLIS = 250;
    private static final String INSIGHTS_PATH = "/api/sync/v1/insights";

    private final ElevateProps props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;
    private final Clock clock;

    @Autowired
    public ElevateClient(ElevateProps props, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this(props, createRestClient(props, restClientBuilder), objectMapper, Thread::sleep, Clock.systemUTC());
    }

    ElevateClient(ElevateProps props, RestClient restClient, ObjectMapper objectMapper) {
        this(props, restClient, objectMapper, Thread::sleep, Clock.systemUTC());
    }

    ElevateClient(ElevateProps props, RestClient restClient, ObjectMapper objectMapper, Sleeper sleeper) {
        this(props, restClient, objectMapper, sleeper, Clock.systemUTC());
    }

    ElevateClient(ElevateProps props, RestClient restClient, ObjectMapper objectMapper, Sleeper sleeper, Clock clock) {
        this.props = props;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    static ClientHttpRequestFactorySettings requestFactorySettings(ElevateProps props) {
        return ClientHttpRequestFactorySettings.defaults().withTimeouts(props.connectTimeout(), props.readTimeout());
    }

    private static RestClient createRestClient(ElevateProps props, RestClient.Builder restClientBuilder) {
        RestClient.Builder builder = restClientBuilder
                .clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(requestFactorySettings(props)));
        if (props.configured()) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }

    public void reportStatus() {
        requireConfigured();
        try {
            String token = authenticate();
            var response = withTransientRetries(() -> restClient
                    .post()
                    .uri("/api/sync/v1/status")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StatusReport("support", props.agentName(), props.supportBotUrl(), props.version()))
                    .retrieve()
                    .toBodilessEntity());
            if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
                throw new ElevateApiException("Elevate status endpoint returned HTTP "
                        + response.getStatusCode().value() + ", expected 204");
            }
        } catch (ElevateApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw httpFailure(e);
        } catch (RestClientException e) {
            throw transportFailure(e);
        }
    }

    public ElevateSnapshot fetchSnapshot() {
        requireConfigured();
        try {
            TokenSession session = new TokenSession(authenticate());
            Resources<ElevateProduct> products = fetchAll("products", ElevateProduct.class, session);
            Resources<ElevateUser> users = fetchAll("users", ElevateUser.class, session);
            Resources<ElevateJourney> journeys = fetchAll("journeys", ElevateJourney.class, session);
            return new ElevateSnapshot(
                    products.items(),
                    users.items(),
                    journeys.items(),
                    payloadsById(products, ElevateProduct::id),
                    payloadsById(users, ElevateUser::id),
                    payloadsById(journeys, ElevateJourney::id));
        } catch (ElevateApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw httpFailure(e);
        } catch (RestClientException e) {
            throw transportFailure(e);
        } catch (IllegalArgumentException e) {
            throw new ElevateApiException("Elevate returned an invalid insights response", e);
        }
    }

    private String authenticate() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        OAuthTokenResponse response = withTransientRetries(() -> {
            @Nullable OAuthTokenResponse tokenResponse = restClient
                    .post()
                    .uri("/api/sync/v1/auth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
            if (tokenResponse == null) {
                throw new ElevateApiException("Elevate token endpoint returned no access token");
            }
            return tokenResponse;
        });
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ElevateApiException("Elevate token endpoint returned no access token");
        }
        return response.accessToken();
    }

    private <T> Resources<T> fetchAll(String resource, Class<T> itemType, TokenSession session) {
        List<T> result = new ArrayList<>();
        List<JsonNode> payloads = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        @Nullable String cursor = null;
        do {
            Page<T> page = fetchPageWithRetry(resource, cursor, itemType, session);
            result.addAll(page.items());
            payloads.addAll(page.payloads());
            cursor = page.nextCursor();
            if (cursor != null && !seenCursors.add(cursor)) {
                throw new ElevateApiException("Elevate returned a repeated " + resource + " cursor");
            }
        } while (cursor != null);
        return new Resources<>(List.copyOf(result), List.copyOf(payloads));
    }

    private <T> Page<T> fetchPageWithRetry(
            String resource, @Nullable String cursor, Class<T> itemType, TokenSession session) {
        try {
            return withTransientRetries(() -> fetchPage(resource, cursor, itemType, session.token));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw e;
            }
            log.info("Elevate access token was rejected during sync; refreshing it once");
            session.token = authenticate();
            return withTransientRetries(() -> fetchPage(resource, cursor, itemType, session.token));
        }
    }

    private <T> Page<T> fetchPage(String resource, @Nullable String cursor, Class<T> itemType, String token) {
        JsonNode response = restClient
                .get()
                .uri(pageUri(resource, cursor))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.isObject()) {
            throw new ElevateApiException("Elevate returned an invalid " + resource + " page");
        }
        JsonNode itemsNode = response.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new ElevateApiException("Elevate returned a " + resource + " page without an items array");
        }
        List<T> items = new ArrayList<>();
        List<JsonNode> payloads = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(objectMapper.convertValue(itemNode, itemType));
            payloads.add(itemNode.deepCopy());
        }

        JsonNode cursorNode = response.get("nextCursor");
        if (cursorNode == null) {
            throw new ElevateApiException("Elevate returned a " + resource + " page without nextCursor");
        }
        @Nullable String nextCursor = null;
        if (!cursorNode.isNull()) {
            if (!cursorNode.isTextual() || cursorNode.textValue().isBlank()) {
                throw new ElevateApiException("Elevate returned an invalid " + resource + " cursor");
            }
            nextCursor = cursorNode.textValue();
        }
        return new Page<>(List.copyOf(items), List.copyOf(payloads), nextCursor);
    }

    private URI pageUri(String resource, @Nullable String cursor) {
        StringBuilder value = new StringBuilder(props.baseUrl())
                .append(INSIGHTS_PATH)
                .append('/')
                .append(resource)
                .append("?limit=")
                .append(PAGE_LIMIT);
        if (cursor != null) {
            value.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }
        return URI.create(value.toString());
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw new IllegalStateException("Elevate is not configured");
        }
    }

    private <T> T withTransientRetries(Request<T> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return request.execute();
            } catch (RestClientResponseException failure) {
                if (attempt >= MAX_ATTEMPTS
                        || !isRetryable(failure.getStatusCode().value())) {
                    throw failure;
                }
                @Nullable ServerRetryDelay serverRetryDelay = serverRetryDelay(failure);
                if (serverRetryDelay != null && serverRetryDelay.exceedsMaximum()) {
                    throw new ElevateApiException(
                            "Elevate returned HTTP " + failure.getStatusCode().value()
                                    + " with Retry-After beyond the configured maximum",
                            failure);
                }
                waitBeforeRetry(attempt, serverRetryDelay == null ? null : serverRetryDelay.delay());
            } catch (RestClientException failure) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw failure;
                }
                waitBeforeRetry(attempt, null);
            }
        }
    }

    private void waitBeforeRetry(int failedAttempt, @Nullable Duration serverRetryDelay) {
        long exponentialCap = BASE_RETRY_DELAY_MILLIS << (failedAttempt - 1);
        long delay = serverRetryDelay == null
                ? ThreadLocalRandom.current().nextLong(exponentialCap + 1)
                : serverRetryDelay.toMillis();
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ElevateApiException("Elevate retry was interrupted", failure);
        }
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private @Nullable ServerRetryDelay serverRetryDelay(RestClientResponseException failure) {
        if (failure.getResponseHeaders() == null) {
            return null;
        }
        @Nullable String value = failure.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            BigInteger seconds = new BigInteger(normalized);
            if (seconds.compareTo(BigInteger.valueOf(props.maxServerRetryDelay().toSeconds())) > 0) {
                return ServerRetryDelay.overMaximum();
            }
            return ServerRetryDelay.withinMaximum(Duration.ofSeconds(seconds.longValueExact()));
        }
        try {
            Instant retryAt = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            Instant now = clock.instant();
            Duration delay = retryAt.isAfter(now) ? Duration.between(now, retryAt) : Duration.ZERO;
            return delay.compareTo(props.maxServerRetryDelay()) > 0
                    ? ServerRetryDelay.overMaximum()
                    : ServerRetryDelay.withinMaximum(delay);
        } catch (RuntimeException invalidDate) {
            return null;
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static <T, I> Map<I, JsonNode> payloadsById(Resources<T> resources, Function<T, I> id) {
        Map<I, JsonNode> payloads = new LinkedHashMap<>();
        for (int index = 0; index < resources.items().size(); index++) {
            payloads.put(
                    id.apply(resources.items().get(index)), resources.payloads().get(index));
        }
        return Map.copyOf(payloads);
    }

    private static ElevateApiException httpFailure(RestClientResponseException failure) {
        return new ElevateApiException(
                "Elevate returned HTTP " + failure.getStatusCode().value(), failure);
    }

    private static ElevateApiException transportFailure(RestClientException failure) {
        if (hasCause(failure, UnknownHostException.class)) {
            return new ElevateApiException("Elevate request failed: DNS lookup failed", failure);
        }
        if (hasCause(failure, SSLException.class)) {
            return new ElevateApiException("Elevate request failed: TLS negotiation failed", failure);
        }
        if (hasCause(failure, SocketTimeoutException.class)
                || hasCause(failure, HttpTimeoutException.class)
                || hasCause(failure, TimeoutException.class)) {
            return new ElevateApiException("Elevate request failed: request timed out", failure);
        }
        if (hasCause(failure, ConnectException.class)) {
            return new ElevateApiException("Elevate request failed: connection could not be established", failure);
        }
        return new ElevateApiException("Elevate request failed: transport error", failure);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        @Nullable Throwable cause = failure;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OAuthTokenResponse(
            @JsonProperty("access_token") @Nullable String accessToken) {}

    private record StatusReport(String type, String name, String url, String version) {}

    private record Page<T>(
            List<T> items,
            List<JsonNode> payloads,
            @Nullable String nextCursor) {}

    private record Resources<T>(List<T> items, List<JsonNode> payloads) {}

    private record ServerRetryDelay(Duration delay, boolean exceedsMaximum) {
        private static ServerRetryDelay withinMaximum(Duration delay) {
            return new ServerRetryDelay(delay, false);
        }

        private static ServerRetryDelay overMaximum() {
            return new ServerRetryDelay(Duration.ZERO, true);
        }
    }

    private static final class TokenSession {
        private String token;

        private TokenSession(String token) {
            this.token = token;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @FunctionalInterface
    private interface Request<T> {
        T execute();
    }
}
