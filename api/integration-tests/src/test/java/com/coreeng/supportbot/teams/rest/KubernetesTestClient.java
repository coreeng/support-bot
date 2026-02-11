package com.coreeng.supportbot.teams.rest;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesTestClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesTestClient.class);

    private static final int DEPLOYMENT_TIMEOUT_MINUTES = 10;
    private static final int PORT_FORWARD_TIMEOUT_SECONDS = 120;
    private static final int PORT_FORWARD_RETRY_INTERVAL_MS = 1000;

    private final KubernetesClient client;
    private LocalPortForward localPortForward;

    public KubernetesTestClient() {
        this.client = new KubernetesClientBuilder().build();
    }

    public void waitUntilDeploymentReady(String deploymentName, String namespace) {
        LOGGER.info("Waiting for deployment {} to be ready...", deploymentName);
        try {
            client.apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .waitUntilReady(DEPLOYMENT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            LOGGER.info("Deployment {} ready.", deploymentName);
        } catch (Exception e) {
            throw new RuntimeException("Deployment " + deploymentName + " failed to become ready within timeout", e);
        }
    }

    public Pod getPodForDeployment(String deploymentName, String namespace) {
        LOGGER.info("Getting pod for deployment {}...", deploymentName);
        try {
            var pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", deploymentName)
                    .list()
                    .getItems();

            if (pods.isEmpty()) {
                throw new RuntimeException("No pods found for deployment " + deploymentName);
            }

            Pod pod = pods.getFirst();
            if (!"Running".equals(pod.getStatus().getPhase())) {
                throw new RuntimeException("Pod for deployment " + deploymentName + " is not running. Current phase: "
                        + pod.getStatus().getPhase());
            }
            LOGGER.info("Pod {} is running.", pod.getMetadata().getName());
            return pod;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get pod for deployment " + deploymentName, e);
        }
    }

    public String getDeploymentLogs(String name, String namespace) {
        LOGGER.info("Getting deployment logs for {}...", name);
        try {
            return client.apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .getLog(true);
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve logs for deployment {}: {}", name, e.getMessage());
            return "[Failed to retrieve logs: " + e.getMessage() + "]";
        }
    }

    public void portForward(String podName, String namespace, int localPort, int remotePort)
            throws InterruptedException {
        LOGGER.info("Starting port-forward for pod {}...", podName);
        localPortForward =
                client.pods().inNamespace(namespace).withName(podName).portForward(remotePort, localPort);

        // Wait for port-forward to establish and be accessible
        boolean portForwardReady = false;
        for (int i = 0; i < PORT_FORWARD_TIMEOUT_SECONDS; i++) {
            try (Socket ignored = new Socket("localhost", localPort)) {
                portForwardReady = true;
                LOGGER.info("Port-forward is ready.");
                break;
            } catch (IOException e) {
                LOGGER.info("Waiting for port-forward to be ready... (attempt {})", i + 1);
                Thread.sleep(PORT_FORWARD_RETRY_INTERVAL_MS);
            }
        }

        if (!portForwardReady) {
            throw new RuntimeException("Port-forward failed to establish within the timeout.");
        }
    }

    public void createOrUpdateConfigMap(String configMapName, String namespace, ConfigMapTeamData data) {
        LOGGER.info(
                "Creating ConfigMap: name={}, namespace={}, data.name={}, data.groupRef={}",
                configMapName,
                namespace,
                data.name(),
                data.groupRef());

        try {
            ConfigMap configMap = new ConfigMapBuilder()
                    .withNewMetadata()
                    .withName(configMapName)
                    .withNamespace(namespace)
                    .endMetadata()
                    .addToData("name", data.name())
                    .addToData("groupRef", data.groupRef())
                    .build();

            client.configMaps().inNamespace(namespace).resource(configMap).createOr(NonDeletingOperation::update);
            LOGGER.info("ConfigMap created.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ConfigMap " + configMapName + " in namespace " + namespace, e);
        }
    }

    public void deleteConfigMap(String name, String namespace) {
        LOGGER.info("Deleting ConfigMap {} in namespace {}...", name, namespace);
        try {
            client.configMaps().inNamespace(namespace).withName(name).delete();
            LOGGER.info("ConfigMap {} deleted.", name);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete ConfigMap {}: {}", name, e.getMessage());
        }
    }

    @Override
    public void close() {
        if (localPortForward != null) {
            try {
                localPortForward.close();
                LOGGER.info("Port-forward closed.");
            } catch (IOException e) {
                LOGGER.error("Error closing port-forward: {}", e.getMessage());
            }
        }
        try {
            client.close();
            LOGGER.info("Kubernetes client closed.");
        } catch (Exception e) {
            LOGGER.error("Error closing Kubernetes client: {}", e.getMessage());
        }
    }
}
