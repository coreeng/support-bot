package com.coreeng.supportbot.teams;

import static org.junit.jupiter.api.Assertions.*;

import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(crud = true)
class GenericPlatformTeamsFetcherTest {
    @SuppressWarnings("NullAway") // initialised by @EnableKubernetesMockClient
    private KubernetesClient k8sClient;

    @Test
    void shouldFetchTeamsSuccessfully() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")), result);
    }

    @Test
    void shouldSkipTeamWhenTeamNameIsMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.missingName",
                "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - team is skipped due to extraction failure
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipTeamWhenGroupRefIsMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.missingGroupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - team is skipped due to extraction failure
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipOnlyTeamsWithExtractionFailuresAndReturnTheRest() {
        // given - 3 teams: 2 with valid groupRef annotation, 1 without
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        k8sClient
                .resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName("team-no-annotation")
                        .endMetadata()
                        .build())
                .createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team3", "group3")).createOr(NonDeletingOperation::update);

        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef" // will fail for team-no-annotation
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
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnOnlyResourcesMatchingFilter_labelSelector() {
        // given
        k8sClient
                .resource(createMockNs("team1", "group1")
                        .edit()
                        .editMetadata()
                        .withLabels(Map.of("team", "true"))
                        .endMetadata()
                        .build())
                .createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team2", "group2")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, "team=true"),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")), result);
    }

    @Test
    void shouldReturnOnlyResourcesMatchingFilter_nameRegexp() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team2", "group2")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter("^.*1$", null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")), result);
    }

    @Test
    void shouldExtractValueUsingOptionalSyntax_whenFieldPresent() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.?annotations.?groupRef.orValue('default-group')");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")), result);
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
                "resource.metadata.?annotations.?groupRef.orValue('default-group')");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(
                List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team-no-annotations", "default-group")), result);
    }

    @Test
    void shouldExtractAttributes() {
        // given
        k8sClient
                .resource(createMockNs("team1", "group1")
                        .edit()
                        .editMetadata()
                        .addToAnnotations("tier", "gold")
                        .addToAnnotations("owner", "platform-team")
                        .endMetadata()
                        .build())
                .createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef",
                Map.of(
                        "tier", "resource.metadata.annotations.tier",
                        "owner", "resource.metadata.?annotations.?owner"));

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(1, result.size());
        assertEquals(
                ImmutableMap.of("tier", "gold", "owner", "platform-team"),
                result.getFirst().attributes());
    }

    @Test
    void shouldLeaveAttributeUnset_whenOptionalFieldMissing() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef",
                Map.of("tier", "resource.metadata.?annotations.?tier"));

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")), result);
        assertTrue(result.getFirst().attributes().isEmpty());
    }

    @Test
    void shouldKeepTeamWithoutTheAttribute_whenAttributeExtractionFails() {
        // given - the 'tier' annotation is missing, and the 'name' attribute's expression yields a map, not a string
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef",
                Map.of(
                        "tier", "resource.metadata.annotations.tier",
                        "name", "resource.metadata",
                        "team", "resource.metadata.name"));

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - the team is returned with only the attribute that could be extracted
        assertEquals(1, result.size());
        assertEquals(ImmutableMap.of("team", "team1"), result.getFirst().attributes());
    }

    @Test
    void shouldLeaveAttributeUnset_whenValueIsBlank() {
        // given
        k8sClient
                .resource(createMockNs("team1", "group1")
                        .edit()
                        .editMetadata()
                        .addToAnnotations("tier", " ")
                        .endMetadata()
                        .build())
                .createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef",
                Map.of("tier", "resource.metadata.annotations.tier"));

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertEquals(List.of(new PlatformTeamsFetcher.TeamAndGroupTuple("team1", "group1")), result);
    }

    @Test
    void shouldFail_whenAttributeExpressionIsMalformed() {
        // given
        k8sClient.resource(createMockNs("team1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.name",
                "resource.metadata.annotations.groupRef",
                Map.of("broken", "resource.metadata.("));

        // when
        IllegalStateException e = assertThrows(IllegalStateException.class, fetcher::fetchTeams);

        // then
        assertEquals("Invalid CEL expression for team attribute 'broken'", e.getMessage());
    }

    @Test
    void shouldFindTheSameTeams_whenEveryAttributeFailsOnTheResources() {
        // given - a missing field, a non-string result, and a three-step optional chain, which cel-java can't
        // evaluate when the fields are present
        k8sClient
                .resource(createMockNs("team1", "group1")
                        .edit()
                        .editMetadata()
                        .addToAnnotations("tier", "gold")
                        .endMetadata()
                        .build())
                .createOr(NonDeletingOperation::update);
        k8sClient.resource(createMockNs("team2", "group2")).createOr(NonDeletingOperation::update);
        String teamName = "resource.metadata.name";
        String groupRef = "resource.metadata.annotations.groupRef";
        List<PlatformTeamsFetcher.TeamAndGroupTuple> withoutAttributes = createGenericTeamsFetcher(
                        new GenericPlatformTeamsFetcher.Filter(null, null), teamName, groupRef)
                .fetchTeams();
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                teamName,
                groupRef,
                Map.of(
                        "missing", "resource.metadata.annotations.nope",
                        "not-a-string", "resource.metadata",
                        "three-steps", "resource.?metadata.?annotations.?tier"));

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then - exactly the teams found without attributes, none of them with an attribute
        assertEquals(2, result.size());
        assertEquals(withoutAttributes, result);
    }

    @Test
    void shouldSkipTeam_whenChainedOptionalTeamNameIsEmpty() {
        // given
        k8sClient.resource(createMockNs("ns1", "group1")).createOr(NonDeletingOperation::update);
        GenericPlatformTeamsFetcher fetcher = createGenericTeamsFetcher(
                new GenericPlatformTeamsFetcher.Filter(null, null),
                "resource.metadata.?labels.?team",
                "resource.metadata.annotations.groupRef");

        // when
        List<PlatformTeamsFetcher.TeamAndGroupTuple> result = fetcher.fetchTeams();

        // then
        assertTrue(result.isEmpty());
    }

    private Namespace createMockNs(String teamName, String groupRef) {
        return new NamespaceBuilder()
                .withNewMetadata()
                .withName(teamName)
                .withAnnotations(Map.of("groupRef", groupRef))
                .endMetadata()
                .build();
    }

    @NotNull private GenericPlatformTeamsFetcher createGenericTeamsFetcher(
            GenericPlatformTeamsFetcher.Filter filter, String teamNameExpr, String groupRefExpr) {
        return createGenericTeamsFetcher(filter, teamNameExpr, groupRefExpr, Map.of());
    }

    @NotNull private GenericPlatformTeamsFetcher createGenericTeamsFetcher(
            GenericPlatformTeamsFetcher.Filter filter,
            String teamNameExpr,
            String groupRefExpr,
            Map<String, String> attributeExprs) {
        GenericPlatformTeamsFetcher.Config config = new GenericPlatformTeamsFetcher.Config(
                "v1",
                "",
                "Namespace",
                "",
                filter,
                new GenericPlatformTeamsFetcher.CelExpression(teamNameExpr),
                new GenericPlatformTeamsFetcher.CelExpression(groupRefExpr),
                Maps.transformValues(attributeExprs, GenericPlatformTeamsFetcher.CelExpression::new));
        return new GenericPlatformTeamsFetcher(config, k8sClient, new JsonMapper());
    }
}
