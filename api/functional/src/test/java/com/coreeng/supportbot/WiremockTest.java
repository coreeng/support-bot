package com.coreeng.supportbot;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;

/**
 * Test class to verify that Wiremock servers are running correctly.
 */
public class WiremockTest {
    private static final Logger logger = LoggerFactory.getLogger(WiremockTest.class);

    /**
     * Tests that all Wiremock servers are running on their respective ports.
     * This test relies on the WiremockSetupLauncherSessionListener to start the servers.
     */
    @Test
    public void testWiremockServersAreRunning() {
        // Test Slack Wiremock (port 8000)
        given()
            .when()
            .get("http://localhost:8000/__admin")
            .then()
            .assertThat()
            .statusCode(200);

        // Test Kubernetes Wiremock (port 8001)
        given()
            .when()
            .get("http://localhost:8001/__admin")
            .then()
            .assertThat()
            .statusCode(200);

        // Test Azure Wiremock (port 8002)
        given()
            .when()
            .get("http://localhost:8002/__admin")
            .then()
            .assertThat()
            .statusCode(200);

        // Test GCP Wiremock (port 8003)
        given()
            .when()
            .get("http://localhost:8003/__admin")
            .then()
            .assertThat()
            .statusCode(200);

        logger.info("All Wiremock servers are running correctly");
    }
}