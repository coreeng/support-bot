package com.coreeng.supportbot.teams.rest;

import io.fabric8.kubernetes.api.model.Pod;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("smoke")
public class ServiceStartupTest {

    private static final Logger logger = LoggerFactory.getLogger(ServiceStartupTest.class);

    // Test data constants
    private static final String testConfigMapName = "tenant-core-test";
    private static final String testTeamName = "core";
    private static final String testGroupRef = "2d5ac5ab-4acc-457e-af4d-117bd135671c";
    
    private static Config config;
    private static HelmClient helmClient;
    private static KubernetesTestClient kubernetesClient;

    @BeforeAll
    static void setup() throws Exception {
        try {
            config = Config.load();

            helmClient = new HelmClient();
            kubernetesClient = new KubernetesTestClient();

            ConfigMapTeamData teamData = new ConfigMapTeamData(testTeamName, testGroupRef);
            kubernetesClient.createOrUpdateConfigMap(testConfigMapName, config.namespace(), teamData);

            if (helmClient.isReleaseDeployed(config.helm().releaseName(), config.namespace())) {
                helmClient.uninstall(config.helm().releaseName(), config.namespace());
            }
            helmClient.install(config.helm().releaseName(), config.helm().chartPath(), config.namespace());
            kubernetesClient.waitUntilDeploymentReady(config.service().deployment().name(), config.namespace());

            if (logger.isDebugEnabled()) {
                logger.debug("Pod logs:\n{}", kubernetesClient.getDeploymentLogs(config.service().deployment().name(), config.namespace()));
            }

            if (config.portForwarding().enabled()) {
                Pod pod = kubernetesClient.getPodForDeployment(config.service().deployment().name(), config.namespace());
                kubernetesClient.portForward(pod.getMetadata().getName(), config.namespace(),
                    config.portForwarding().localPort(), config.portForwarding().remotePort());
                RestAssured.baseURI = "http://localhost:" + config.portForwarding().localPort();
            } else {
                RestAssured.baseURI = "http://" + config.service().deployment().name() + "." + config.namespace() + ".svc.cluster.local:" + config.portForwarding().remotePort();
            }
        } catch (Exception e) {
            logger.error("Error during setup", e);
            throw e;
        }
    }

    @AfterAll
    static void cleanup() {
        try {
            if (helmClient != null) {
                helmClient.uninstall(config.helm().releaseName(), config.namespace());
            }
        } catch (Exception e) {
            logger.error("Error during cleanup, couldn't uninstall service chart", e);
        }

        try {
            if (kubernetesClient != null) {
                kubernetesClient.deleteConfigMap(testConfigMapName, config.namespace());
                kubernetesClient.close();
            }
        } catch (Exception e) {
            logger.error("Error during cleanup, couldn't delete ConfigMap", e);
        }
    }

    @Test
    void shouldReturnTeams() {
        List<TeamUI> teams = when()
            .get("/team")
            .then()
            .statusCode(200)
            .log().body()
            .extract().body().jsonPath().getList("", TeamUI.class);

        TeamUI coreTeam = teams.stream()
            .filter(team -> testTeamName.equals(team.name()))
            .findFirst()
            .orElse(null);

        assertThat(coreTeam)
            .as("Team '%s' should be present in response", testTeamName)
            .isNotNull();
        assertThat(coreTeam.name())
            .as("Team name should match expected value")
            .isEqualTo(testTeamName);
        assertThat(coreTeam.types())
            .as("Team types should contain expected values")
            .containsExactlyInAnyOrder("tenant", "l2Support");
        assertThat(teams)
            .as("Response should contain at least one team")
            .isNotEmpty();
    }
}