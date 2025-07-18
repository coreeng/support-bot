package com.coreeng.supportbot;

import com.coreeng.supportbot.wiremock.AzureWiremock;
import com.coreeng.supportbot.wiremock.GcpWiremock;
import com.coreeng.supportbot.wiremock.KubernetesWiremock;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import com.coreeng.supportbot.wiremock.WiremockManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.*;

import static io.restassured.RestAssured.given;

/**
 * Test class to verify that Wiremock servers are running correctly.
 * Demonstrates injection of wiremock instances using the WiremockInjectionExtension.
 */
@ExtendWith(WiremockInjectionExtension.class)
public class WiremockTest {
    private static final Logger logger = LoggerFactory.getLogger(WiremockTest.class);

    // These fields will be injected by WiremockInjectionExtension based on their types
    private WiremockManager wiremockManager;
    private SlackWiremock slackWiremock;
    private KubernetesWiremock kubernetesWiremock;
    private AzureWiremock azureWiremock;
    private GcpWiremock gcpWiremock;

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

    /**
     * Tests that wiremock instances are properly injected and can be used directly.
     * This demonstrates the new injection functionality.
     */
    @Test
    public void testWiremockInstancesAreInjected() {
        // Verify that all instances are injected
        assertThat(wiremockManager).as("WiremockManager should be injected").isNotNull();
        assertThat(slackWiremock).as("SlackWiremock should be injected").isNotNull();
        assertThat(kubernetesWiremock).as("KubernetesWiremock should be injected").isNotNull();
        assertThat(azureWiremock).as("AzureWiremock should be injected").isNotNull();
        assertThat(gcpWiremock).as("GcpWiremock should be injected").isNotNull();

        // Verify that the injected instances are the same as those in the manager
        assertThat(slackWiremock).as("Injected SlackWiremock should be the same instance as in manager").isSameAs(wiremockManager.slackWiremock);
        assertThat(kubernetesWiremock).as("Injected KubernetesWiremock should be the same instance as in manager").isSameAs(wiremockManager.kubernetesWiremock);
        assertThat(azureWiremock).as("Injected AzureWiremock should be the same instance as in manager").isSameAs(wiremockManager.azureWiremock);
        assertThat(gcpWiremock).as("Injected GcpWiremock should be the same instance as in manager").isSameAs(wiremockManager.gcpWiremock);

        // Verify that the servers are running by checking their ports
        assertThat(slackWiremock.isRunning()).as("SlackWiremock should be running").isTrue();
        assertThat(kubernetesWiremock.isRunning()).as("KubernetesWiremock should be running").isTrue();
        assertThat(azureWiremock.isRunning()).as("AzureWiremock should be running").isTrue();
        assertThat(gcpWiremock.isRunning()).as("GcpWiremock should be running").isTrue();

        logger.info("All Wiremock instances are properly injected and running");
    }
}
