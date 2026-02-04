package com.coreeng.supportbot.teams.rest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import static io.restassured.RestAssured.when;

@Tag("integration")
@Tag("smoke")
public class ServiceStartupTest {

    private static final Logger logger = LoggerFactory.getLogger(ServiceStartupTest.class);

    // Test data constants
    private static final String testTeamCMName = "tenant-core-test";
    private static final String testTeamName = "core";
    private static final String testGroupRef = "2d5ac5ab-4acc-457e-af4d-117bd135671c";

    private static final String testBadRefTeamCMName = "tenant-bad-ref-test";
    private static final String testBadRefTeamName = "bad-ref";
    private static final String testBadGroupRef = "bad ref";

    private static Config config;
    private static KubernetesTestClient kubernetesClient;

    @BeforeAll
    static void setup() throws Exception {
        try {
            config = Config.load();

            kubernetesClient = new KubernetesTestClient();

            ConfigMapTeamData teamData = new ConfigMapTeamData(testTeamName, testGroupRef);
            kubernetesClient.createOrUpdateConfigMap(testTeamCMName, config.namespace(), teamData);
            ConfigMapTeamData notExistingTeamData = new ConfigMapTeamData(testBadRefTeamName, testBadGroupRef);
            kubernetesClient.createOrUpdateConfigMap(testBadRefTeamCMName, config.namespace(), notExistingTeamData);

            runServiceScript("deploy");
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

            // Configure RestAssured with default auth bypass headers
            RestAssured.requestSpecification = new RequestSpecBuilder()
                .addHeader("X-Test-User", "test@integration.test")
                .addHeader("X-Test-Role", "support")
                .build();
            logger.info("RestAssured configured with test auth bypass headers");
        } catch (Exception e) {
            logger.error("Error during setup", e);
            throw e;
        }
    }

    @AfterAll
    static void cleanup() {
        try {
            runServiceScript("delete");
        } catch (Exception e) {
            logger.error("Error during cleanup, couldn't uninstall service chart", e);
        }

        try {
            if (kubernetesClient != null) {
                kubernetesClient.deleteConfigMap(testTeamCMName, config.namespace());
                kubernetesClient.deleteConfigMap(testBadRefTeamCMName, config.namespace());
                kubernetesClient.close();
            }
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
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

        // Both teams should be present (including the one with malformed groupRef - service handles fetch failure gracefully)
        assertThat(teams)
            .extracting(TeamUI::code)
            .as("Both teams should be present in response")
            .contains(testTeamName, testBadRefTeamName);

        assertThat(findTeamByCode(teams, testTeamName).types())
            .as("Team '%s' types should contain expected values", testTeamName)
            .containsExactlyInAnyOrder("tenant", "escalation");
        assertThat(findTeamByCode(teams, testBadRefTeamName).types())
            .as("Team '%s' types should contain expected values", testTeamName)
            .containsExactly("tenant");
    }

    private TeamUI findTeamByCode(List<TeamUI> teams, String code) {
        return teams.stream()
            .filter(team -> code.equals(team.code()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Team with code '" + code + "' not found"));
    }

    private static void runServiceScript(String action) throws Exception {
        String scriptPath = config.service().deploymentScript().scriptPath();
        String chartPath = config.service().deploymentScript().chartPath();
        String helmRelease = config.service().deploymentScript().releaseName();
        ProcessBuilder pb = new ProcessBuilder(scriptPath);
        Map<String, String> env = pb.environment();
        env.put("ACTION", action);
        env.put("NAMESPACE", config.namespace());
        env.put("SERVICE_RELEASE", helmRelease);
        env.put("SERVICE_CHART_PATH", chartPath);
        env.put("SERVICE_IMAGE_REPOSITORY", config.service().image().repository());
        env.put("SERVICE_IMAGE_TAG", config.service().image().tag());
        env.put("VALUES_FILE", config.service().deploymentScript().valuesFilePath());
        env.put("WAIT_TIMEOUT", "60");
        env.put("REDEPLOY", action.equals("deploy") ? "true" : "false");
        env.put("DEPLOY_DB", "false");
        env.put("HELM_DRIVER", "configmap");

        pb.inheritIO();
        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("deploy-service.sh failed with exit code " + code + " (action=" + action + ")");
        }
    }
}