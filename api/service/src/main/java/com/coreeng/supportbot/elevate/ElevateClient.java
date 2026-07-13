package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String INSIGHTS_PATH = "/api/sync/v1/insights";

    private final ElevateProps props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ElevateClient(ElevateProps props, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this(
                props,
                props.configured()
                        ? restClientBuilder.clone().baseUrl(props.baseUrl()).build()
                        : restClientBuilder.clone().build(),
                objectMapper);
    }

    ElevateClient(ElevateProps props, RestClient restClient, ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public void reportStatus() {
        requireConfigured();
        try {
            String token = authenticate();
            var response = restClient
                    .post()
                    .uri("/api/sync/v1/status")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StatusReport("support", props.agentName(), props.supportBotUrl(), props.version()))
                    .retrieve()
                    .toBodilessEntity();
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
            List<ElevateProduct> products = fetchAll("products", ElevateProduct.class, session);
            List<ElevateUser> users = fetchAll("users", ElevateUser.class, session);
            List<ElevateJourney> journeys = fetchAll("journeys", ElevateJourney.class, session);
            return new ElevateSnapshot(products, users, journeys);
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

        OAuthTokenResponse response = restClient
                .post()
                .uri("/api/sync/v1/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuthTokenResponse.class);
        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()) {
            throw new ElevateApiException("Elevate token endpoint returned no access token");
        }
        return response.accessToken();
    }

    private <T> List<T> fetchAll(String resource, Class<T> itemType, TokenSession session) {
        List<T> result = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        @Nullable String cursor = null;
        do {
            Page<T> page = fetchPageWithRetry(resource, cursor, itemType, session);
            result.addAll(page.items());
            cursor = page.nextCursor();
            if (cursor != null && !seenCursors.add(cursor)) {
                throw new ElevateApiException("Elevate returned a repeated " + resource + " cursor");
            }
        } while (cursor != null);
        return List.copyOf(result);
    }

    private <T> Page<T> fetchPageWithRetry(
            String resource, @Nullable String cursor, Class<T> itemType, TokenSession session) {
        try {
            return fetchPage(resource, cursor, itemType, session.token);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw e;
            }
            log.info("Elevate access token was rejected during sync; refreshing it once");
            session.token = authenticate();
            return fetchPage(resource, cursor, itemType, session.token);
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
        for (JsonNode itemNode : itemsNode) {
            items.add(objectMapper.convertValue(itemNode, itemType));
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
        return new Page<>(List.copyOf(items), nextCursor);
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static ElevateApiException httpFailure(RestClientResponseException failure) {
        return new ElevateApiException(
                "Elevate returned HTTP " + failure.getStatusCode().value(), failure);
    }

    private static ElevateApiException transportFailure(RestClientException failure) {
        return new ElevateApiException(
                "Elevate request failed (" + failure.getClass().getSimpleName() + ")", failure);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OAuthTokenResponse(
            @JsonProperty("access_token") @Nullable String accessToken) {}

    private record StatusReport(String type, String name, String url, String version) {}

    private record Page<T>(List<T> items, @Nullable String nextCursor) {}

    private static final class TokenSession {
        private String token;

        private TokenSession(String token) {
            this.token = token;
        }
    }
}
