package com.coreeng.supportbot.teams.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tier 2: in-cluster Job completes Dex LDAP authorization-code flow, exchanges the code once via
 * {@code POST /auth/oauth/exchange}, then checks API JWT teams (jwt-groups) and {@code GET /auth/me}.
 *
 * <p>Requires LDAP, Dex (issuer aligned with {@code DEX_ISSUER_URI}, typically {@code
 * values-dex-oidc-incluster.yaml}), and Support Bot deployed with {@code
 * values-integrationtests-oidc.yaml} (or equivalent). Secrets: {@code dex-secrets} ({@code
 * client-id}, {@code client-secret}), {@code integration-ldap-test-user} ({@code password} for
 * bootstrap user {@code alice@supportbot.local}).
 *
 * <p>Runs by default. Disable with {@code DISABLE_INTEGRATION_LDAP_DEX_TESTS=true}.
 */
@Tag("integration")
@Tag("oidc")
@Order(3)
@DisabledIfEnvironmentVariable(named = "DISABLE_INTEGRATION_LDAP_DEX_TESTS", matches = "true")
public class DexOidcInClusterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DexOidcInClusterTest.class);

    private static final String JOB_TEMPLATE_RESOURCE = "k8s/dex-ldap-oidc-job.yaml";
    private static final String OIDC_SCRIPT_RESOURCE = "k8s/dex-ldap-oidc-flow.py";

    private static Config config;
    private static KubernetesTestClient kubernetesClient;
    private static String jobName;
    private static String configMapName;

    @BeforeAll
    static void setup() throws IOException {
        config = Config.load();
        kubernetesClient = new KubernetesTestClient();

        String deploymentName = config.service().deployment().name();
        LOGGER.info("Verifying API deployment '{}' is ready before OIDC test...", deploymentName);
        kubernetesClient.waitUntilDeploymentReady(deploymentName, config.namespace());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jobName = "support-bot-dex-ldap-oidc-" + suffix;
        configMapName = "support-bot-dex-ldap-oidc-script-" + suffix;
    }

    @AfterAll
    static void cleanup() {
        try {
            if (config != null) {
                undeployService();
            }
        } catch (Exception e) {
            LOGGER.error("Error undeploying service", e);
        }
        try {
            if (kubernetesClient != null) {
                if (configMapName != null && config != null) {
                    kubernetesClient.deleteConfigMap(configMapName, config.namespace());
                }
                kubernetesClient.close();
            }
        } catch (Exception e) {
            LOGGER.error("Error during cleanup", e);
        }
    }

    private static void undeployService() throws Exception {
        String scriptPath = config.service().deploymentScript().scriptPath();
        String chartPath = config.service().deploymentScript().chartPath();
        String helmRelease = config.service().deploymentScript().releaseName();
        ProcessBuilder pb = new ProcessBuilder(scriptPath);
        Map<String, String> env = pb.environment();
        env.put("ACTION", "delete");
        env.put("NAMESPACE", config.namespace());
        env.put("SERVICE_RELEASE", helmRelease);
        env.put("SERVICE_CHART_PATH", chartPath);
        env.put("SERVICE_IMAGE_REPOSITORY", config.service().image().repository());
        env.put("SERVICE_IMAGE_TAG", config.service().image().tag());
        env.put("VALUES_FILE", config.service().deploymentScript().valuesFilePath());
        env.put("HELM_DRIVER", "configmap");

        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
        int code = p.waitFor();
        LOGGER.info("deploy-service.sh (action=delete) output:\n{}", output);
        if (code != 0) {
            LOGGER.error("deploy-service.sh delete failed with exit code {}", code);
        }
    }

    @Test
    void dexOidcFlowAgainstApi() throws IOException {
        String script = loadClasspathResource(OIDC_SCRIPT_RESOURCE);
        kubernetesClient.createOrReplaceConfigMapData(configMapName, config.namespace(), "oidc.py", script);

        String yaml = loadJobTemplate(jobName, config.namespace(), configMapName);
        kubernetesClient.applyYamlManifest(yaml, config.namespace());

        String logs;
        try {
            kubernetesClient.waitUntilJobComplete(jobName, config.namespace());
        } finally {
            logs = kubernetesClient.getJobPodLogs(jobName, config.namespace());
            LOGGER.info("Dex LDAP OIDC Job logs:\n{}", logs);
        }

        assertThat(logs).contains("OK_TOKEN");
        assertThat(logs).contains("OK_GROUPS");
        assertThat(logs).contains("OK_API");
        assertThat(logs).contains("OK_ALL");
    }

    private static String loadClasspathResource(String resourcePath) throws IOException {
        try (InputStream in = DexOidcInClusterTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String loadJobTemplate(String name, String namespace, String cmName) throws IOException {
        try (InputStream in = DexOidcInClusterTest.class.getClassLoader().getResourceAsStream(JOB_TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + JOB_TEMPLATE_RESOURCE);
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return raw.replace("PLACEHOLDER_JOB_NAME", name)
                    .replace("PLACEHOLDER_NAMESPACE", namespace)
                    .replace("PLACEHOLDER_CONFIGMAP_NAME", cmName);
        }
    }
}
