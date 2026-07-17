package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
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
    private final InsightsResponseBudgetContext responseBudgetContext;

    @Autowired
    public ElevateClient(ElevateProps props, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.sleeper = Thread::sleep;
        this.clock = Clock.systemUTC();
        this.responseBudgetContext = new InsightsResponseBudgetContext();
        this.restClient = createRestClient(props, restClientBuilder, responseBudgetContext);
    }

    ElevateClient(
            ElevateProps props,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Sleeper sleeper,
            Clock clock) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
        this.clock = clock;
        this.responseBudgetContext = new InsightsResponseBudgetContext();
        this.restClient = restClientBuilder
                .clone()
                .requestInterceptor(
                        insightsResponseSizeInterceptor(props.maxInsightsPageResponseBytes(), responseBudgetContext))
                .build();
    }

    static ClientHttpRequestFactorySettings requestFactorySettings(ElevateProps props) {
        return ClientHttpRequestFactorySettings.defaults().withTimeouts(props.connectTimeout(), props.readTimeout());
    }

    private static RestClient createRestClient(
            ElevateProps props,
            RestClient.Builder restClientBuilder,
            InsightsResponseBudgetContext responseBudgetContext) {
        RestClient.Builder builder = restClientBuilder
                .clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(requestFactorySettings(props)))
                .requestInterceptor(
                        insightsResponseSizeInterceptor(props.maxInsightsPageResponseBytes(), responseBudgetContext));
        if (props.configured()) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }

    private static ClientHttpRequestInterceptor insightsResponseSizeInterceptor(
            long maximumBytes, InsightsResponseBudgetContext responseBudgetContext) {
        return (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            return request.getURI().getPath().contains(INSIGHTS_PATH + "/")
                    ? new SizeLimitedClientHttpResponse(response, maximumBytes, responseBudgetContext)
                    : response;
        };
    }

    public void reportStatus() {
        requireConfigured();
        try {
            String token = authenticate(null);
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

    public synchronized ElevateSnapshot fetchSnapshot() {
        requireConfigured();
        FetchDeadline deadline = new FetchDeadline(clock.instant(), props.syncTimeout());
        responseBudgetContext.begin(props.maxInsightsSnapshotResponseBytes());
        try {
            TokenSession session = new TokenSession(authenticate(deadline));
            FetchBudget budget = new FetchBudget(props.maxTotalEntities(), props.maxMaterializedRelationships());
            Resources<ElevateProduct> products =
                    fetchAll("products", ElevateProduct.class, session, budget, deadline, ignored -> 0);
            Resources<ElevateUser> users =
                    fetchAll("users", ElevateUser.class, session, budget, deadline, ignored -> 0);
            Resources<ElevateJourney> journeys = fetchAll(
                    "journeys",
                    ElevateJourney.class,
                    session,
                    budget,
                    deadline,
                    ElevateClient::distinctJourneyUserCount);
            validateJourneyProducts(products.items(), journeys.items());
            ElevateSnapshot snapshot = new ElevateSnapshot(
                    products.items(),
                    users.items(),
                    journeys.items(),
                    payloadsById(products, ElevateProduct::id),
                    payloadsById(users, ElevateUser::id),
                    payloadsById(journeys, ElevateJourney::id));
            deadline.requireWithinLimit(clock);
            return snapshot;
        } catch (ElevateApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw httpFailure(e);
        } catch (RestClientException e) {
            throw transportFailure(e);
        } catch (IllegalArgumentException e) {
            throw new ElevateApiException("Elevate returned an invalid insights response", e);
        } finally {
            responseBudgetContext.clear();
        }
    }

    private String authenticate(@Nullable FetchDeadline deadline) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        OAuthTokenResponse response = withTransientRetries(
                () -> {
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
                },
                deadline);
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ElevateApiException("Elevate token endpoint returned no access token");
        }
        return response.accessToken();
    }

    private <T> Resources<T> fetchAll(
            String resource,
            Class<T> itemType,
            TokenSession session,
            FetchBudget budget,
            FetchDeadline deadline,
            ToLongFunction<JsonNode> relationshipCount) {
        List<T> result = new ArrayList<>();
        List<JsonNode> payloads = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        @Nullable String cursor = null;
        int pagesFetched = 0;
        do {
            pagesFetched++;
            Page<T> page = fetchPageWithRetry(resource, cursor, itemType, session, budget, deadline, relationshipCount);
            result.addAll(page.items());
            payloads.addAll(page.payloads());
            cursor = page.nextCursor();
            if (cursor != null && !seenCursors.add(cursor)) {
                throw new ElevateApiException("Elevate returned a cyclic " + resource + " cursor");
            }
            if (cursor != null && pagesFetched >= props.maxPagesPerResource()) {
                throw new ElevateApiException("Elevate " + resource + " pagination exceeded the configured limit of "
                        + props.maxPagesPerResource() + " pages");
            }
        } while (cursor != null);
        return new Resources<>(List.copyOf(result), List.copyOf(payloads));
    }

    private static long distinctJourneyUserCount(JsonNode journey) {
        JsonNode userIds = journey.get("userIds");
        if (userIds == null || !userIds.isArray()) {
            return 0;
        }
        Set<JsonNode> distinctUserIds = new HashSet<>();
        userIds.forEach(distinctUserIds::add);
        return distinctUserIds.size();
    }

    private static void validateJourneyProducts(List<ElevateProduct> products, List<ElevateJourney> journeys) {
        Set<String> productIds = new HashSet<>();
        for (ElevateProduct product : products) {
            productIds.add(product.id());
        }
        for (ElevateJourney journey : journeys) {
            if (!productIds.contains(journey.productId())) {
                throw new ElevateApiException(
                        "Elevate returned a journey whose product was absent from the fetched snapshot");
            }
        }
    }

    private <T> Page<T> fetchPageWithRetry(
            String resource,
            @Nullable String cursor,
            Class<T> itemType,
            TokenSession session,
            FetchBudget budget,
            FetchDeadline deadline,
            ToLongFunction<JsonNode> relationshipCount) {
        try {
            return withTransientRetries(
                    () -> fetchPage(resource, cursor, itemType, session.token, budget, relationshipCount), deadline);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw e;
            }
            log.info("Elevate access token was rejected during sync; refreshing it once");
            session.token = authenticate(deadline);
            return withTransientRetries(
                    () -> fetchPage(resource, cursor, itemType, session.token, budget, relationshipCount), deadline);
        }
    }

    private <T> Page<T> fetchPage(
            String resource,
            @Nullable String cursor,
            Class<T> itemType,
            String token,
            FetchBudget budget,
            ToLongFunction<JsonNode> relationshipCount) {
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
        if (itemsNode.size() > PAGE_LIMIT) {
            throw new ElevateApiException(
                    "Elevate returned a " + resource + " page exceeding the requested " + PAGE_LIMIT + " items");
        }
        budget.addEntities(resource, itemsNode.size());
        for (JsonNode itemNode : itemsNode) {
            budget.addRelationships(resource, relationshipCount.applyAsLong(itemNode));
        }
        List<T> items = new ArrayList<>();
        List<JsonNode> payloads = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(objectMapper.convertValue(itemNode, itemType));
            payloads.add(itemNode);
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
        return withTransientRetries(request, null);
    }

    private <T> T withTransientRetries(Request<T> request, @Nullable FetchDeadline deadline) {
        for (int attempt = 1; ; attempt++) {
            requireWithinLimit(deadline);
            try {
                T response = request.execute();
                requireWithinLimit(deadline);
                return response;
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
                waitBeforeRetry(attempt, serverRetryDelay == null ? null : serverRetryDelay.delay(), deadline);
            } catch (RestClientException failure) {
                if (hasCause(failure, InsightsResponseSizeExceededException.class)
                        || hasCause(failure, InsightsSnapshotResponseSizeExceededException.class)
                        || attempt >= MAX_ATTEMPTS) {
                    throw failure;
                }
                waitBeforeRetry(attempt, null, deadline);
            }
        }
    }

    private void waitBeforeRetry(
            int failedAttempt, @Nullable Duration serverRetryDelay, @Nullable FetchDeadline deadline) {
        long exponentialCap = BASE_RETRY_DELAY_MILLIS << (failedAttempt - 1);
        long delay = serverRetryDelay == null
                ? ThreadLocalRandom.current().nextLong(exponentialCap + 1)
                : serverRetryDelay.toMillis();
        if (deadline != null) {
            deadline.requireCanWait(clock, Duration.ofMillis(delay));
        }
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ElevateApiException("Elevate retry was interrupted", failure);
        }
    }

    private void requireWithinLimit(@Nullable FetchDeadline deadline) {
        if (deadline != null) {
            deadline.requireWithinLimit(clock);
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

    private ElevateApiException transportFailure(RestClientException failure) {
        if (hasCause(failure, InsightsSnapshotResponseSizeExceededException.class)) {
            return new ElevateApiException(
                    "Elevate insights snapshot exceeded the configured cumulative response size limit of "
                            + props.maxInsightsSnapshotResponseBytes() + " bytes",
                    failure);
        }
        if (hasCause(failure, InsightsResponseSizeExceededException.class)) {
            return new ElevateApiException(
                    "Elevate insights page exceeded the configured response size limit of "
                            + props.maxInsightsPageResponseBytes() + " bytes",
                    failure);
        }
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

    private static final class SizeLimitedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final long maximumBytes;
        private final InsightsResponseBudgetContext responseBudgetContext;
        private @Nullable InputStream body;

        private SizeLimitedClientHttpResponse(
                ClientHttpResponse delegate, long maximumBytes, InsightsResponseBudgetContext responseBudgetContext) {
            this.delegate = delegate;
            this.maximumBytes = maximumBytes;
            this.responseBudgetContext = responseBudgetContext;
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            InputStream current = body;
            if (current == null) {
                current = new SizeLimitedInputStream(delegate.getBody(), maximumBytes, responseBudgetContext);
                body = current;
            }
            return current;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class SizeLimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private final InsightsResponseBudgetContext responseBudgetContext;
        private long bytesRead;

        private SizeLimitedInputStream(
                InputStream delegate, long maximumBytes, InsightsResponseBudgetContext responseBudgetContext) {
            super(delegate);
            this.maximumBytes = maximumBytes;
            this.responseBudgetContext = responseBudgetContext;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                recordBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            long remaining = maximumBytes - bytesRead;
            long maximumRead = remaining == Long.MAX_VALUE ? Long.MAX_VALUE : remaining + 1;
            int boundedLength = (int) Math.min(length, maximumRead);
            int count = super.read(bytes, offset, boundedLength);
            if (count > 0) {
                recordBytes(count);
            }
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count <= 0) {
                return 0;
            }
            long remaining = maximumBytes - bytesRead;
            long maximumSkip = remaining == Long.MAX_VALUE ? Long.MAX_VALUE : remaining + 1;
            long skipped = super.skip(Math.min(count, maximumSkip));
            if (skipped > 0) {
                recordBytes(skipped);
            }
            return skipped;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public synchronized void mark(int readLimit) {}

        @Override
        public synchronized void reset() throws IOException {
            throw new IOException("mark/reset is not supported");
        }

        private void recordBytes(long count) throws IOException {
            if (bytesRead > maximumBytes - count) {
                throw new InsightsResponseSizeExceededException();
            }
            bytesRead += count;
            responseBudgetContext.recordBytes(count);
        }
    }

    private static final class InsightsResponseSizeExceededException extends IOException {}

    private static final class InsightsSnapshotResponseSizeExceededException extends IOException {}

    private static final class InsightsResponseBudgetContext {
        private @Nullable InsightsResponseBudget current;

        private synchronized void begin(long maximumBytes) {
            if (current != null) {
                throw new IllegalStateException("An Elevate insights fetch is already active");
            }
            current = new InsightsResponseBudget(maximumBytes);
        }

        private synchronized void recordBytes(long count) throws InsightsSnapshotResponseSizeExceededException {
            @Nullable InsightsResponseBudget budget = current;
            if (budget != null) {
                budget.recordBytes(count);
            }
        }

        private synchronized void clear() {
            current = null;
        }
    }

    private static final class InsightsResponseBudget {
        private final long maximumBytes;
        private long bytesRead;

        private InsightsResponseBudget(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        private void recordBytes(long count) throws InsightsSnapshotResponseSizeExceededException {
            if (bytesRead > maximumBytes - count) {
                throw new InsightsSnapshotResponseSizeExceededException();
            }
            bytesRead += count;
        }
    }

    private record FetchDeadline(Instant startedAt, Duration timeout) {
        private void requireWithinLimit(Clock clock) {
            if (!clock.instant().isBefore(startedAt.plus(timeout))) {
                throw exceeded();
            }
        }

        private void requireCanWait(Clock clock, Duration delay) {
            Instant now = clock.instant();
            Instant deadline = startedAt.plus(timeout);
            if (!now.isBefore(deadline) || delay.compareTo(Duration.between(now, deadline)) >= 0) {
                throw exceeded();
            }
        }

        private static ElevateApiException exceeded() {
            return new ElevateApiException("Elevate insights sync exceeded the configured time limit");
        }
    }

    private static final class FetchBudget {
        private final long maxEntities;
        private final long maxRelationships;
        private long entities;
        private long relationships;

        private FetchBudget(long maxEntities, long maxRelationships) {
            this.maxEntities = maxEntities;
            this.maxRelationships = maxRelationships;
        }

        private void addEntities(String resource, long count) {
            entities = addWithinLimit(
                    entities,
                    count,
                    maxEntities,
                    "Elevate snapshot exceeded the configured total entity limit while fetching " + resource);
        }

        private void addRelationships(String resource, long count) {
            relationships = addWithinLimit(
                    relationships,
                    count,
                    maxRelationships,
                    "Elevate snapshot exceeded the configured materialized relationship limit while fetching "
                            + resource);
        }

        private static long addWithinLimit(long current, long increment, long limit, String message) {
            if (increment < 0 || current > limit - increment) {
                throw new ElevateApiException(message);
            }
            return current + increment;
        }
    }

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
