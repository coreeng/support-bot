package com.coreeng.supportbot;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.Config;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKitExtension;
import io.restassured.filter.log.LogDetail;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Escalation dashboard endpoints, through the real security chain and database. */
@ExtendWith(TestKitExtension.class)
public class EscalationDashboardApiTests {

    private static final String TEST_BYPASS_USER = "test@functional.test";
    private static final String SUPPORT_ENGINEER_ROLE = "support";
    private static final String LEADERSHIP_ROLE = "leadership";

    // Not "leadership": TestAuthBypassFilter grants it SUPPORT_ENGINEER too. "escalation" has neither.
    private static final String NON_DASHBOARD_ROLE = "escalation";

    // A fixed past window that no test seeds tickets into, so the responses are empty (or all zero) and deterministic.
    private static final String EMPTY_WINDOW = "?dateFrom=1999-01-04&dateTo=1999-01-08";

    private static final List<String> ESCALATION_ENDPOINTS = List.of(
            "/dashboard/escalation-rate",
            "/dashboard/resolution-escalated-vs-not",
            "/dashboard/escalation-team-scorecard",
            "/dashboard/escalation-topics",
            "/dashboard/time-before-escalation",
            "/dashboard/escalation-rate-by-team");

    private Config config;
    private SupportBotClient supportBotClient;

    @Test
    void escalationEndpoints_areLimitedToLeadershipAndSupportEngineers() {
        for (String endpoint : ESCALATION_ENDPOINTS) {
            String path = endpoint + EMPTY_WINDOW;
            assertThat(supportBotClient.getStatusCodeAsRole(path, NON_DASHBOARD_ROLE))
                    .as(endpoint)
                    .isEqualTo(403);
            assertThat(supportBotClient.getStatusCodeAsRole(path, SUPPORT_ENGINEER_ROLE))
                    .as(endpoint)
                    .isEqualTo(200);
            assertThat(supportBotClient.getStatusCodeAsRole(path, LEADERSHIP_ROLE))
                    .as(endpoint)
                    .isEqualTo(200);
        }
    }

    @Test
    void escalationEndpoints_returnEmptyShapesForAnEmptyWindow() {
        var rate = get(SUPPORT_ENGINEER_ROLE, "/dashboard/escalation-rate" + EMPTY_WINDOW);
        assertThat(rate.statusCode()).isEqualTo(200);
        assertThat(rate.jsonPath().getString("granularity")).isEqualTo("DAY");
        // Every day of the window is present, with no tickets.
        assertThat(rate.jsonPath().getList("data.date"))
                .containsExactly("1999-01-04", "1999-01-05", "1999-01-06", "1999-01-07", "1999-01-08");
        assertThat(rate.jsonPath().getList("data.tickets", Long.class)).containsOnly(0L);

        var wait = get(SUPPORT_ENGINEER_ROLE, "/dashboard/time-before-escalation" + EMPTY_WINDOW);
        assertThat(wait.statusCode()).isEqualTo(200);
        assertThat(wait.jsonPath().getLong("escalations")).isZero();

        for (String endpoint : List.of(
                "/dashboard/resolution-escalated-vs-not",
                "/dashboard/escalation-team-scorecard",
                "/dashboard/escalation-topics",
                "/dashboard/escalation-rate-by-team")) {
            var response = get(SUPPORT_ENGINEER_ROLE, endpoint + EMPTY_WINDOW);
            assertThat(response.statusCode()).as(endpoint).isEqualTo(200);
            assertThat(response.jsonPath().getList("$")).as(endpoint).isEmpty();
        }
    }

    private Response get(String role, String path) {
        return given().header("X-Test-User", TEST_BYPASS_USER)
                .header("X-Test-Role", role)
                .when()
                .get(config.supportBot().baseUrl() + path)
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .extract()
                .response();
    }
}
