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
 * <p>Requires LDAP, Dex (issuer aligned with {@code DEX_ISSUER_URI}; Helm overlays typically {@code
 * values-integration.yaml}, {@code values-integration-ldap-plaintext-ephemeral.yaml}, then {@code
 * values-dex-oidc-incluster.yaml} via {@code make dex-deploy-integration} from repo root), and
 * Support Bot deployed with {@code
 * values-integrationtests-oidc.yaml} (or equivalent). Secrets: {@code dex-secrets} ({@code
 * client-id}, {@code client-secret}), {@code integration-ldap-test-user} ({@code password} for
 * bootstrap user {@code alice@supportbot.local}).
 *
 * <p>Runs by default. Disable with {@code DISABLE_INTEGRATION_LDAP_DEX_TESTS=true}.
 *
 * <p><b>Lifecycle:</b> The API service is deployed by {@link ServiceStartupTest @Order(2)} and
 * undeployed externally — by the Makefile {@code undeploy-integration} target (local) or by
 * {@code cleanup_job()} in {@code run-integration-tests.sh} (pipeline). Test classes are pure
 * consumers and never manage the API service lifecycle.
 */
@Tag("integration")
@Tag("oidc")
@Order(3)
@DisabledIfEnvironmentVariable(named = "DISABLE_INTEGRATION_LDAP_DEX_TESTS", matches = "true")
public class DexOidcInClusterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DexOidcInClusterTest.class);

    private static final String JOB_TEMPLATE_RESOURCE = "k8s/dex-ldap-oidc-job.yaml";
    private static final String OIDC_SCRIPT_RESOURCE = "k8s/dex-ldap-oidc-flow.py";
    private static final String OIDC_REQUIREMENTS_RESOURCE = "k8s/oidc-requirements.txt";

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

    @Test
    void dexOidcFlowAgainstApi() throws IOException {
        String script = loadClasspathResource(OIDC_SCRIPT_RESOURCE);
        String requirements = loadClasspathResource(OIDC_REQUIREMENTS_RESOURCE);
        kubernetesClient.createOrReplaceConfigMapData(
                configMapName, config.namespace(), Map.of("oidc.py", script, "requirements.txt", requirements));

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
