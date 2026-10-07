package com.coreeng.supportbot.teams.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(crud = true)
class KubernetesTestClientTest {
    @SuppressWarnings("NullAway") // initialized by @EnableKubernetesMockClient
    private KubernetesClient k8sClient;

    @SuppressWarnings("NullAway") // initialized by @EnableKubernetesMockClient
    private KubernetesMockServer mockServer;

    @Test
    void appliesConfigMapsYamlJobsAndReadsJobPodLogs() {
        try (KubernetesTestClient client = new KubernetesTestClient(k8sClient)) {
            client.createOrUpdateConfigMap("team-config", "default", new ConfigMapTeamData("Team One", "groups/one"));
            assertThat(k8sClient
                            .configMaps()
                            .inNamespace("default")
                            .withName("team-config")
                            .get()
                            .getData())
                    .containsEntry("name", "Team One")
                    .containsEntry("groupRef", "groups/one");

            client.createOrReplaceConfigMapData("team-config", "default", "script", "echo ready");
            assertThat(k8sClient
                            .configMaps()
                            .inNamespace("default")
                            .withName("team-config")
                            .get()
                            .getData())
                    .containsEntry("script", "echo ready");
            client.deleteConfigMap("team-config", "default");
            assertThat(k8sClient
                            .configMaps()
                            .inNamespace("default")
                            .withName("team-config")
                            .get())
                    .isNull();

            client.applyYamlManifest("""
                    apiVersion: batch/v1
                    kind: Job
                    metadata:
                      name: script-job
                    status:
                      succeeded: 1
                    """, "default");
            client.waitUntilJobComplete("script-job", "default", Duration.ofSeconds(3));

            Pod jobPod = pod("script-job-pod", "default", "job-name", "script-job", "Running");
            k8sClient.resource(jobPod).createOrReplace();
            mockServer
                    .expect()
                    .get()
                    .withPath("/api/v1/namespaces/default/pods/script-job-pod/log?pretty=false")
                    .andReturn(200, "script output")
                    .once();
            assertThat(client.getJobPodLogs("script-job", "default")).isEqualTo("script output");
            client.deleteJob("script-job", "default");
        }
    }

    @Test
    void findsRunningDeploymentPodAndReportsMissingOrUnreadyPods() {
        try (KubernetesTestClient client = new KubernetesTestClient(k8sClient)) {
            k8sClient
                    .resource(pod("api-pod", "default", "app.kubernetes.io/name", "api", "Running"))
                    .createOrReplace();
            assertThat(client.getPodForDeployment("api", "default")
                            .getMetadata()
                            .getName())
                    .isEqualTo("api-pod");

            assertThatThrownBy(() -> client.getPodForDeployment("missing", "default"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to get pod for deployment missing");

            k8sClient
                    .resource(pod("pending-pod", "default", "app.kubernetes.io/name", "pending", "Pending"))
                    .createOrReplace();
            assertThatThrownBy(() -> client.getPodForDeployment("pending", "default"))
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(error -> assertThat(error.getCause()).hasMessageContaining("is not running"));
        }
    }

    @Test
    void closesPortForwardBeforeClientAndStillClosesClientWhenForwardCloseFails() {
        List<String> closeOrder = new ArrayList<>();
        KubernetesClient client = proxy(KubernetesClient.class, (proxy, method, args) -> {
            if (method.getName().equals("close")) {
                closeOrder.add("client");
            }
            return null;
        });
        LocalPortForward portForward = proxy(LocalPortForward.class, (proxy, method, args) -> {
            if (method.getName().equals("close")) {
                closeOrder.add("port-forward");
                throw new IOException("mock close failure");
            }
            return null;
        });

        new KubernetesTestClient(client, portForward).close();

        assertThat(closeOrder).containsExactly("port-forward", "client");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler invocationHandler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocationHandler);
    }

    private static Pod pod(String name, String namespace, String label, String value, String phase) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToLabels(label, value)
                .endMetadata()
                .withNewStatus()
                .withPhase(phase)
                .endStatus()
                .build();
    }
}
