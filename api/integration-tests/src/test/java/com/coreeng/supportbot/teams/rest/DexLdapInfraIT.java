package com.coreeng.supportbot.teams.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tier 1 smoke: in-cluster Job checks Dex telemetry health and LDAP seed user {@code uid=alice}.
 *
 * <p>Requires LDAP and Dex already installed in the integration namespace (see README), and Secret
 * {@code ldap-secrets} with key {@code admin-password}.
 *
 * <p>Enable with {@code INTEGRATION_LDAP_DEX_SMOKE=true}. Run with tag {@code ldap-infra}, e.g. {@code
 * ./gradlew :integration-tests:test --tests '*DexLdapInfraIT'}.
 */
@Tag("integration")
@Tag("ldap-infra")
@EnabledIfEnvironmentVariable(named = "INTEGRATION_LDAP_DEX_SMOKE", matches = "true")
public class DexLdapInfraIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(DexLdapInfraIT.class);

    private static final String JOB_TEMPLATE_RESOURCE = "k8s/dex-ldap-infra-job.yaml";

    private static Config config;
    private static KubernetesTestClient kubernetesClient;
    private static String jobName;

    @BeforeAll
    static void setup() throws IOException {
        config = Config.load();
        kubernetesClient = new KubernetesTestClient();
        jobName = "support-bot-dex-ldap-infra-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterAll
    static void cleanup() {
        try {
            if (kubernetesClient != null && config != null && jobName != null) {
                kubernetesClient.deleteJob(jobName, config.namespace());
                kubernetesClient.close();
            }
        } catch (Exception e) {
            LOGGER.error("Error during cleanup", e);
        }
    }

    @Test
    void dexAndLdapReachableInCluster() throws IOException {
        String yaml = loadJobTemplate(jobName, config.namespace());
        kubernetesClient.applyYamlManifest(yaml, config.namespace());
        kubernetesClient.waitUntilJobComplete(jobName, config.namespace());
        String logs = kubernetesClient.getJobPodLogs(jobName, config.namespace());
        LOGGER.info("Dex/LDAP infra Job logs:\n{}", logs);

        assertThat(logs).contains("OK_DEX");
        assertThat(logs).contains("OK_LDAP");
        assertThat(logs).contains("OK_ALL");
    }

    private static String loadJobTemplate(String name, String namespace) throws IOException {
        try (InputStream in = DexLdapInfraIT.class.getClassLoader().getResourceAsStream(JOB_TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + JOB_TEMPLATE_RESOURCE);
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return raw.replace("PLACEHOLDER_JOB_NAME", name).replace("PLACEHOLDER_NAMESPACE", namespace);
        }
    }
}
