package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.coreeng.supportbot.config.ElevateProps;
import com.coreeng.supportbot.util.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElevateClientTest {
    private static final String BASE_URL = "https://elevate.example.test";
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private MockRestServiceServer server;
    private ElevateClient client;
    private RestClient.Builder restClientBuilder;
    private ObjectMapper objectMapper;
    private List<Long> retryDelays;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new JsonMapper().getObjectMapper();
        retryDelays = new ArrayList<>();
        client = new ElevateClient(
                configuredProps(), restClientBuilder, objectMapper, retryDelays::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void statusAuthenticatesWithOAuthFormAndPostsExactSupportAgentPayload() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/status"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "type": "support",
                          "name": "Support Bot",
                          "url": "https://support.example.test",
                          "version": "1.2.3"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.reportStatus();

        server.verify();
    }

    @Test
    void productionRequestFactoryUsesConfiguredFiniteTimeouts() {
        var settings = ElevateClient.requestFactorySettings(configuredProps());

        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void statusRequiresNoContentResponse() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/status"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::reportStatus)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate status endpoint returned HTTP 200, expected 204");
    }

    @Test
    void rejectsPageWithoutExplicitNextCursorRatherThanPersistingAPartialSnapshot() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned a products page without nextCursor");
        server.verify();
    }

    @Test
    void followsOpaquePaginationCursorAndMapsExactInsightsDtos() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "product-1",
                            "slug": "product-one",
                            "name": "Product One",
                            "customer": null,
                            "createdAt": "2026-01-02T03:04:05",
                            "lastUpdatedAt": "2026-02-03T04:05:06",
                            "futureField": "ignored"
                          }],
                          "nextCursor": "opaque+/= value"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500&cursor=opaque%2B%2F%3D+value"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "product-2",
                            "slug": "product-two",
                            "name": "Product Two",
                            "customer": "Customer",
                            "createdAt": "2026-03-04T05:06:07",
                            "lastUpdatedAt": "2026-04-05T06:07:08"
                          }],
                          "nextCursor": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/users?limit=500"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "fb7ad3c3-c175-4e72-8955-7d0138f29029",
                            "productId": "product-1",
                            "name": "Operator",
                            "description": "Runs the platform",
                            "createdAt": "2026-01-02T03:04:05",
                            "lastUpdatedAt": "2026-02-03T04:05:06"
                          }],
                          "nextCursor": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/journeys?limit=500"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "journey-1",
                            "slug": "first-journey",
                            "name": "First Journey",
                            "productId": "product-1",
                            "productSlug": "product-one",
                            "userDescription": null,
                            "primaryProblems": "Slow feedback",
                            "userIds": ["fb7ad3c3-c175-4e72-8955-7d0138f29029"],
                            "createdAt": "2026-01-02T03:04:05",
                            "lastUpdatedAt": "2026-02-03T04:05:06"
                          }],
                          "nextCursor": null
                        }
                        """, MediaType.APPLICATION_JSON));

        ElevateSnapshot snapshot = client.fetchSnapshot();

        assertThat(snapshot.products()).extracting(ElevateProduct::id).containsExactly("product-1", "product-2");
        assertThat(Objects.requireNonNull(snapshot.productPayloads().get("product-1"))
                        .get("futureField")
                        .textValue())
                .isEqualTo("ignored");
        assertThat(snapshot.users()).singleElement().satisfies(user -> {
            assertThat(user.productId()).isEqualTo("product-1");
            assertThat(user.description()).isEqualTo("Runs the platform");
        });
        assertThat(snapshot.journeys()).singleElement().satisfies(journey -> {
            assertThat(journey.productSlug()).isEqualTo("product-one");
            assertThat(journey.userIds()).hasSize(1);
        });
        server.verify();
    }

    @Test
    void rejectsCursorCyclesBeforeReplacingTheStoredSnapshot() {
        useProps(configuredProps(10, 20_000, 100_000));
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(page("[]", "\"cursor-a\""));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500&cursor=cursor-a"))
                .andRespond(page("[]", "\"cursor-a\""));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned a cyclic products cursor");
        server.verify();
    }

    @Test
    void rejectsUniqueCursorRunawayAtTheConfiguredPageLimit() {
        useProps(configuredProps(2, 20_000, 100_000));
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(page("[]", "\"cursor-a\""));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500&cursor=cursor-a"))
                .andRespond(page("[]", "\"cursor-b\""));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate products pagination exceeded the configured limit of 2 pages");
        server.verify();
    }

    @Test
    void rejectsSnapshotsBeyondTheConfiguredEntityLimit() {
        useProps(configuredProps(10, 1, 100_000));
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(page("[" + productJson("product-1") + "," + productJson("product-2") + "]", "null"));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate snapshot exceeded the configured total entity limit while fetching products");
        server.verify();
    }

    @Test
    void rejectsPagesBeyondTheRequestedLimitBeforeConvertingItems() {
        expectToken("access-token");
        String invalidItems = String.join(",", java.util.Collections.nCopies(501, "0"));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(page("[" + invalidItems + "]", "null"));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned a products page exceeding the requested 500 items");
        server.verify();
    }

    @Test
    void rejectsAnOversizedInsightsPageDespiteAMisleadingContentLength() {
        ElevateProps props = configuredProps(100, 20_000, 100_000, 256);
        RestClient.Builder limitedBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer limitedServer =
                MockRestServiceServer.bindTo(limitedBuilder).build();
        ElevateClient limitedClient =
                new ElevateClient(props, limitedBuilder, objectMapper, ignored -> {}, Clock.fixed(NOW, ZoneOffset.UTC));
        expectToken(limitedServer, "access-token");
        String response = "{\"items\":[],\"padding\":\"" + "x".repeat(512) + "\",\"nextCursor\":null}";
        limitedServer
                .expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_LENGTH, "1"));

        assertThatThrownBy(limitedClient::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate insights page exceeded the configured response size limit of 256 bytes")
                .hasMessageNotContaining("padding");
        limitedServer.verify();
    }

    @Test
    void rejectsIndividuallyValidPagesBeyondTheCumulativeSnapshotResponseLimit() {
        ElevateProps props = configuredProps(100, 20_000, 100_000, 512, 600, Duration.ofMinutes(10));
        useProps(props);
        expectToken("access-token");
        String padding = "x".repeat(300);
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withSuccess(
                        "{\"items\":[],\"padding\":\"" + padding + "\",\"nextCursor\":\"next\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500&cursor=next"))
                .andRespond(withSuccess(
                        "{\"items\":[],\"padding\":\"" + padding + "\",\"nextCursor\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage(
                        "Elevate insights snapshot exceeded the configured cumulative response size limit of 600 bytes")
                .hasMessageNotContaining(padding);
        server.verify();
    }

    @Test
    void refusesARetryDelayThatWouldExceedTheWholeSyncTimeout() {
        useProps(configuredProps(100, 20_000, 100_000, 16_777_216, 67_108_864, Duration.ofSeconds(10)));
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "11"));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate insights sync exceeded the configured time limit");
        assertThat(retryDelays).isEmpty();
        server.verify();
    }

    @Test
    void rejectsSnapshotsBeyondTheConfiguredMaterializedRelationshipLimit() {
        useProps(configuredProps(10, 20_000, 1));
        expectToken("access-token");
        expectCollection("products", "[" + productJson("product-1") + "]");
        expectCollection("users", "[]");
        expectCollection(
                "journeys",
                "["
                        + journeyJson(
                                "product-1",
                                "[\"11111111-1111-1111-1111-111111111111\",\"22222222-2222-2222-2222-222222222222\"]")
                        + "]");

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage(
                        "Elevate snapshot exceeded the configured materialized relationship limit while fetching journeys");
        server.verify();
    }

    @Test
    void rejectsAJourneyWhoseProductIsAbsent() {
        expectToken("access-token");
        expectCollection("products", "[" + productJson("product-1") + "]");
        expectCollection("users", "[]");
        expectCollection("journeys", "[" + journeyJson("missing-product", "[]") + "]");

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned a journey whose product was absent from the fetched snapshot");
        server.verify();
    }

    @Test
    void retainsAJourneyUserRelationshipWhenTheUserIsAbsent() {
        expectToken("access-token");
        expectCollection("products", "[" + productJson("product-1") + "]");
        expectCollection("users", "[]");
        expectCollection(
                "journeys", "[" + journeyJson("product-1", "[\"33333333-3333-3333-3333-333333333333\"]") + "]");

        ElevateSnapshot snapshot = client.fetchSnapshot();

        assertThat(snapshot.journeys().getFirst().userIds())
                .containsExactly(java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"));
        server.verify();
    }

    @Test
    void retriesEachUnauthorizedPageOnceSoLongSyncsCanCrossMultipleTokenExpiries() {
        expectToken("expired-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andExpect(header("Authorization", "Bearer expired-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("first-fresh-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andExpect(header("Authorization", "Bearer first-fresh-token"))
                .andRespond(emptyPage());
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/users?limit=500"))
                .andExpect(header("Authorization", "Bearer first-fresh-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("second-fresh-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/users?limit=500"))
                .andExpect(header("Authorization", "Bearer second-fresh-token"))
                .andRespond(emptyPage());
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/journeys?limit=500"))
                .andExpect(header("Authorization", "Bearer second-fresh-token"))
                .andRespond(emptyPage());

        assertThat(client.fetchSnapshot())
                .isEqualTo(new ElevateSnapshot(java.util.List.of(), java.util.List.of(), java.util.List.of()));
        server.verify();
    }

    @Test
    void retriesTransientFailuresAtMostThreeTimes() {
        expectToken("access-token");
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned HTTP 503");
        assertThat(retryDelays).hasSize(2).allSatisfy(delay -> assertThat(delay).isBetween(0L, 500L));
        server.verify();
    }

    @Test
    void honorsNumericRetryAfterAtTheConfiguredLimit() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60"));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(emptyPage());
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/users?limit=500"))
                .andRespond(emptyPage());
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/journeys?limit=500"))
                .andRespond(emptyPage());

        assertThat(client.fetchSnapshot())
                .isEqualTo(new ElevateSnapshot(java.util.List.of(), java.util.List.of(), java.util.List.of()));
        assertThat(retryDelays).containsExactly(60_000L);
        server.verify();
    }

    @Test
    void honorsHttpDateRetryAfter() {
        expectToken("access-token");
        String retryAt =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.plusSeconds(45).atZone(ZoneOffset.UTC));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", retryAt));
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(emptyPage());
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/users?limit=500"))
                .andRespond(emptyPage());
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/journeys?limit=500"))
                .andRespond(emptyPage());

        assertThat(client.fetchSnapshot())
                .isEqualTo(new ElevateSnapshot(java.util.List.of(), java.util.List.of(), java.util.List.of()));
        assertThat(retryDelays).containsExactly(45_000L);
        server.verify();
    }

    @Test
    void doesNotRetryBeforeAnOverBudgetRetryAfter() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "61"));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned HTTP 429 with Retry-After beyond the configured maximum")
                .hasMessageNotContaining("61")
                .hasMessageNotContaining("secret-value");
        assertThat(retryDelays).isEmpty();
        server.verify();
    }

    @Test
    void doesNotRetryRegularClientErrors() {
        expectToken("access-token");
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/products?limit=500"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate returned HTTP 400");
        assertThat(retryDelays).isEmpty();
        server.verify();
    }

    @Test
    void reportsAnActionableSanitizedTransportCauseAfterBoundedRetries() {
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/api/sync/v1/auth/token"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("socket details must not be exposed");
                });

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(ElevateApiException.class)
                .hasMessage("Elevate request failed: request timed out")
                .hasMessageNotContaining("socket details");
        assertThat(retryDelays).hasSize(2);
        server.verify();
    }

    private void expectToken(String token) {
        expectToken(server, token);
    }

    private static void expectToken(MockRestServiceServer target, String token) {
        target.expect(requestTo(BASE_URL + "/api/sync/v1/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .string("grant_type=client_credentials&client_id=esc_client&client_secret=secret-value"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + token
                                + "\",\"expires_in\":300,\"token_type\":\"Bearer\",\"scope\":\"insights:products:read\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectCollection(String resource, String items) {
        server.expect(requestTo(BASE_URL + "/api/sync/v1/insights/" + resource + "?limit=500"))
                .andRespond(page(items, "null"));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator page(
            String items, String nextCursor) {
        return withSuccess("{\"items\":" + items + ",\"nextCursor\":" + nextCursor + "}", MediaType.APPLICATION_JSON);
    }

    private static String productJson(String id) {
        return """
                {
                  "id": "%s",
                  "slug": "%s-slug",
                  "name": "%s name",
                  "customer": null,
                  "createdAt": "2026-01-02T03:04:05",
                  "lastUpdatedAt": "2026-02-03T04:05:06"
                }
                """.formatted(id, id, id);
    }

    private static String journeyJson(String productId, String userIds) {
        return """
                {
                  "id": "journey-1",
                  "slug": "journey-1-slug",
                  "name": "Journey 1",
                  "productId": "%s",
                  "productSlug": "%s-slug",
                  "userDescription": null,
                  "primaryProblems": null,
                  "userIds": %s,
                  "createdAt": "2026-01-02T03:04:05",
                  "lastUpdatedAt": "2026-02-03T04:05:06"
                }
                """.formatted(productId, productId, userIds);
    }

    private void useProps(ElevateProps props) {
        retryDelays = new ArrayList<>();
        client = new ElevateClient(
                props, restClientBuilder, objectMapper, retryDelays::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator emptyPage() {
        return withSuccess("{\"items\":[],\"nextCursor\":null}", MediaType.APPLICATION_JSON);
    }

    private static ElevateProps configuredProps() {
        return configuredProps(100, 20_000, 100_000);
    }

    private static ElevateProps configuredProps(int maxPages, long maxEntities, long maxRelationships) {
        return configuredProps(maxPages, maxEntities, maxRelationships, 16_777_216);
    }

    private static ElevateProps configuredProps(
            int maxPages, long maxEntities, long maxRelationships, long maxResponseBytes) {
        return configuredProps(
                maxPages, maxEntities, maxRelationships, maxResponseBytes, 67_108_864, Duration.ofMinutes(10));
    }

    private static ElevateProps configuredProps(
            int maxPages,
            long maxEntities,
            long maxRelationships,
            long maxResponseBytes,
            long maxSnapshotResponseBytes,
            Duration syncTimeout) {
        return new ElevateProps(
                BASE_URL,
                "esc_client",
                "secret-value",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                maxResponseBytes,
                maxSnapshotResponseBytes,
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                Duration.ofHours(12),
                syncTimeout,
                maxPages,
                maxEntities,
                maxRelationships,
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                "Support Bot",
                "https://support.example.test",
                "1.2.3");
    }
}
