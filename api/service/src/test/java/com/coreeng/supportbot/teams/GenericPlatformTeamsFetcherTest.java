package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.util.JsonMapper;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnableKubernetesMockClient(crud = true)
class GenericPlatformTeamsFetcherTest {
    private KubernetesMockServer k8sServer;
    private KubernetesClient k8sClient;

    @Test
    void shouldFetchTeamsSuccessfully() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "Namespace",
            new GenericPlatformTeamsFetcher.Filter(null, null),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/name"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/annotations/groupRef")
        );
        GenericPlatformTeamsFetcher fetcher = new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")),
            result
        );
    }

    @Test
    void shouldThrowExceptionWhenTeamNameIsMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "Namespace",
            new GenericPlatformTeamsFetcher.Filter(null, null),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/missingName"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/annotations/groupRef")
        );
        GenericPlatformTeamsFetcher fetcher = new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());

        // when & then
        assertThrows(GenericPlatformTeamsFetcher.PropertyExtractionException.class, fetcher::fetchTeams);
    }

    @Test
    void shouldThrowExceptionWhenGroupRefIsMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "Namespace",
            new GenericPlatformTeamsFetcher.Filter(null, null),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/name"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/annotations/missingGroupRef")
        );
        GenericPlatformTeamsFetcher fetcher = new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());

        // when & then
        assertThrows(GenericPlatformTeamsFetcher.PropertyExtractionException.class, fetcher::fetchTeams);
    }

    @Test
    void shouldReturnEmptyListWhenNoResources() {
        // given
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "Namespace",
            new GenericPlatformTeamsFetcher.Filter(null, null),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/name"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/annotations/groupRef")
        );
        GenericPlatformTeamsFetcher fetcher = new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnOnlyResourcesMatchingFilter_labelSelector() {
        // given
        k8sClient.resource(createMockNs("team1", "group1").edit()
            .editMetadata().withLabels(Map.of("team", "true")).endMetadata()
            .build()).createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team2", "group2")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "Namespace",
            new GenericPlatformTeamsFetcher.Filter(null, "team=true"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/name"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/annotations/groupRef")
        );
        GenericPlatformTeamsFetcher fetcher = new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")),
            result
        );
    }

    @Test
    void shouldReturnOnlyResourcesMatchingFilter_nameRegexp() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team2", "group2")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "Namespace",
            new GenericPlatformTeamsFetcher.Filter("^.*1$", null),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/name"),
            new GenericPlatformTeamsFetcher.PropertyPointer("/metadata/annotations/groupRef")
        );
        GenericPlatformTeamsFetcher fetcher = new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")),
            result
        );
    }

    private Namespace createMockNs(String teamName, String groupRef) {
        return new NamespaceBuilder()
            .withNewMetadata()
            .withName(teamName)
            .withAnnotations(Map.of("groupRef", groupRef))
            .endMetadata()
            .build();
    }
}