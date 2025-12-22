package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.util.JsonMapper;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.jetbrains.annotations.NotNull;
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
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
            new GenericPlatformTeamsFetcher.Filter(null, null),
            "resource.metadata.name",
            "resource.metadata.annotations.groupRef"
        );

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")),
            result
        );
    }

    @Test
    void shouldSkipTeamWhenTeamNameIsMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(new GenericPlatformTeamsFetcher.Filter(null, null), "resource.metadata.missingName", "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - team is skipped due to extraction failure
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipTeamWhenGroupRefIsMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(new GenericPlatformTeamsFetcher.Filter(null, null), "resource.metadata.name", "resource.metadata.annotations.missingGroupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - team is skipped due to extraction failure
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipOnlyTeamsWithExtractionFailuresAndReturnTheRest() {
        // given - 3 teams: 2 with valid groupRef annotation, 1 without
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        k8sClient.resource(new NamespaceBuilder()
            .withNewMetadata()
            .withName("team-no-annotation")
            .endMetadata()
            .build()).createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team3", "group3")).createOr(NonDeletingOperation::update);

        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
            new GenericPlatformTeamsFetcher.Filter(null, null),
            "resource.metadata.name",
            "resource.metadata.annotations.groupRef"  // will fail for team-no-annotation
        );

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - only team-no-annotation is skipped, the other two are returned
        assertEquals(2, result.size());
        assertTrue(result.contains(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")));
        assertTrue(result.contains(new PlatformTeamsFetcher.TeamAndGroupTuple("team3", "group3")));
    }

    @Test
    void shouldReturnEmptyListWhenNoResources() {
        // given
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(new GenericPlatformTeamsFetcher.Filter(null, null), "resource.metadata.name", "resource.metadata.annotations.groupRef");

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
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(new GenericPlatformTeamsFetcher.Filter(null, "team=true"), "resource.metadata.name", "resource.metadata.annotations.groupRef");

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
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(new GenericPlatformTeamsFetcher.Filter("^.*1$", null), "resource.metadata.name", "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")),
            result
        );
    }

    @Test
    void shouldExtractValueUsingOptionalSyntax_whenFieldPresent() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
            new GenericPlatformTeamsFetcher.Filter(null, null),
            "resource.metadata.name",
            "resource.metadata.?annotations.?groupRef.orValue('default-group')"
        );

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")),
            result
        );
    }

    @Test
    void shouldUseFallbackValue_whenOptionalFieldMissing() {
        // given
        Namespace nsWithoutAnnotations = new NamespaceBuilder()
            .withNewMetadata()
            .withName("team-no-annotations")
            .endMetadata()
            .build();
        k8sClient.resource(nsWithoutAnnotations).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
            new GenericPlatformTeamsFetcher.Filter(null, null),
            "resource.metadata.name",
            "resource.metadata.?annotations.?groupRef.orValue('default-group')"
        );

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
            List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team-no-annotations", "default-group")),
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

    @NotNull
    private GenericPlatformTeamsFetcher createGenericTeamsFetcher(GenericPlatformTeamsFetcher.Filter filter, String teamNameExpr, String groupRefExpr) {
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
            "v1",
            "",
            "Namespace",
            "",
            filter,
            new GenericPlatformTeamsFetcher.CelExpression(teamNameExpr),
            new GenericPlatformTeamsFetcher.CelExpression(groupRefExpr)
        );
        return new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());
    }
}