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
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElevateClientTest {
    private static final String BASE_URL = "https://elevate.example.test";

    private MockRestServiceServer server;
    private ElevateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new JsonMapper().getObjectMapper();
        client = new ElevateClient(configuredProps(), builder.build(), objectMapper);
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

    private void expectToken(String token) {
        server.expect(requestTo(BASE_URL + "/api/sync/v1/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .string("grant_type=client_credentials&client_id=esc_client&client_secret=secret-value"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + token
                                + "\",\"expires_in\":300,\"token_type\":\"Bearer\",\"scope\":\"insights:products:read\"}",
                        MediaType.APPLICATION_JSON));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator emptyPage() {
        return withSuccess("{\"items\":[],\"nextCursor\":null}", MediaType.APPLICATION_JSON);
    }

    private static ElevateProps configuredProps() {
        return new ElevateProps(
                BASE_URL,
                "esc_client",
                "secret-value",
                Duration.ofHours(1),
                Duration.ofHours(12),
                "Support Bot",
                "https://support.example.test",
                "1.2.3");
    }
}
