package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.ErasureRequestClient;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Elevate's erasure orchestrator submits Erasure Requests and polls them on the internal port (ADR-009, ADR-010). */
@ExtendWith(TestKitExtension.class)
public class ErasureRequestApiTests {

    private static final String CURRENT_EMAIL = "departing.user@example.com";
    private static final String NEW_EMAIL = "erased-user@example.com";

    private TestKit testKit;
    private SupportBotClient supportBotClient;
    private ErasureRequestClient erasureRequests;
    private String requestId;

    @BeforeEach
    void setUp() {
        erasureRequests = testKit.erasureRequests();
        requestId = UUID.randomUUID().toString();
    }

    @Test
    void submittingAnErasureRequestAcceptsItAndReportsItInProgress() {
        Response submitted = erasureRequests.submit(body(requestId, CURRENT_EMAIL, NEW_EMAIL));

        assertThat(submitted.statusCode()).isEqualTo(202);
        assertThat(submitted.jsonPath().getString("requestId")).isEqualTo(requestId);
        assertThat(submitted.jsonPath().getString("status")).isEqualTo("IN_PROGRESS");
        assertThat(submitted.jsonPath().getString("requestedAt")).isNotBlank();
        assertThat(submitted.asString()).doesNotContain(CURRENT_EMAIL, NEW_EMAIL);

        Response polled = erasureRequests.poll(requestId);

        assertThat(polled.statusCode()).isEqualTo(200);
        assertThat(polled.jsonPath().getString("requestId")).isEqualTo(requestId);
        assertThat(polled.jsonPath().getString("status")).isEqualTo("IN_PROGRESS");
        assertThat(polled.jsonPath().getString("requestedAt"))
                .isEqualTo(submitted.jsonPath().getString("requestedAt"));
        assertThat(polled.asString()).doesNotContain(CURRENT_EMAIL, NEW_EMAIL);
    }

    @Test
    void replayingARequestIdConvergesOnTheFirstRequest() {
        Response first = erasureRequests.submit(body(requestId, CURRENT_EMAIL, NEW_EMAIL));

        Response replayed = erasureRequests.submit(body(requestId, "someone.else@example.com", NEW_EMAIL));

        assertThat(replayed.statusCode()).isEqualTo(202);
        assertThat(replayed.jsonPath().getString("requestId")).isEqualTo(requestId);
        assertThat(replayed.jsonPath().getString("status")).isEqualTo("IN_PROGRESS");
        assertThat(replayed.jsonPath().getString("requestedAt"))
                .isEqualTo(first.jsonPath().getString("requestedAt"));
    }

    @Test
    void pollingAnUnknownErasureRequestReturnsNotFound() {
        assertThat(erasureRequests.poll(requestId).statusCode()).isEqualTo(404);
    }

    @Test
    void anInvalidErasureRequestIsRefusedWithoutBeingStoredOrEchoed() {
        Response refused = erasureRequests.submit(Map.of("requestId", requestId, "currentUserEmail", CURRENT_EMAIL));

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.asString()).doesNotContain(CURRENT_EMAIL);
        assertThat(erasureRequests.poll(requestId).statusCode()).isEqualTo(404);
    }

    @Test
    void theInternalPortIsConfinedToTheErasureEndpoints() {
        assertThat(erasureRequests.getOnInternalPort("/ticket").statusCode()).isEqualTo(403);
        assertThat(erasureRequests.getOnInternalPort("/health").statusCode()).isEqualTo(403);
    }

    @Test
    void thePublicPortRefusesErasureRequestsToAnUnauthenticatedCaller() {
        erasureRequests.submit(body(requestId, CURRENT_EMAIL, NEW_EMAIL));

        assertThat(erasureRequests
                        .submitOnPublicPort(body(UUID.randomUUID().toString(), CURRENT_EMAIL, NEW_EMAIL))
                        .statusCode())
                .isEqualTo(401);
        assertThat(erasureRequests.pollOnPublicPort(requestId).statusCode()).isEqualTo(401);
    }

    @Test
    void thePublicPortRefusesErasureRequestsToAPersonWhateverTheirRole() {
        erasureRequests.submit(body(requestId, CURRENT_EMAIL, NEW_EMAIL));

        assertThat(supportBotClient.getStatusCodeAsRole("/api/erasure-requests/" + requestId, "leadership"))
                .isEqualTo(403);
        assertThat(supportBotClient.postStatusCodeAsRole("/api/erasure-requests", "leadership"))
                .isEqualTo(403);
    }

    private static Map<String, String> body(String requestId, String currentUserEmail, String newUserEmail) {
        return Map.of("requestId", requestId, "currentUserEmail", currentUserEmail, "newUserEmail", newUserEmail);
    }
}
