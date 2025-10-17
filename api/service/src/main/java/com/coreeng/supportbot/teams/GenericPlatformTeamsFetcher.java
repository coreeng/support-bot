package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.util.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import org.apache.logging.log4j.util.Strings;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Slf4j
@RequiredArgsConstructor
public class GenericPlatformTeamsFetcher implements PlatformTeamsFetcher {
    private final static Version jqVersion = Versions.JQ_1_7;
    private final static Scope jqScope = Scope.newEmptyScope();
    static {
        BuiltinFunctionLoader.getInstance().loadFunctions(jqVersion, jqScope);
    }

    private final Config config;
    private final KubernetesClient k8sClient;
    private final JsonMapper jsonMapper;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        JsonQuery groupRefQuery = config.groupRef().compile();
        JsonQuery teamNameQuery = config.teamName().compile();

        ListOptions listOptions = new ListOptions();
        if (Strings.isNotBlank(config.filter().labelSelector())) {
            listOptions.setLabelSelector(config.filter().labelSelector());
        }

        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
            .withVersion(config.apiVersion())
            .withGroup(config.apiGroup())
            .withKind(config.kind())
            .withPlural(config.kind().toLowerCase() + "s")
            .withNamespaced(Strings.isNotBlank(config.namespace()))
            .build();
        GenericKubernetesResourceList resourceList = k8sClient
            .genericKubernetesResources(context)
            .list(listOptions);

        List<GenericKubernetesResource> items;
        if (Strings.isNotBlank(config.filter().nameRegexp())) {
            Pattern namePattern = Pattern.compile(config.filter().nameRegexp());
            items = resourceList.getItems().stream()
                .filter(i -> namePattern.matcher(i.getMetadata().getName()).matches())
                .toList();
        } else {
            items = resourceList.getItems();
        }

        return items.stream()
            .map(r -> {
                JsonNode resourceJson = jsonMapper.getObjectMapper().valueToTree(r);
                JsonNode teamNameNode = queryResourceWithJq("teamName", r, teamNameQuery, resourceJson);
                JsonNode groupRefNode = queryResourceWithJq("groupRef", r, groupRefQuery, resourceJson);

                return new TeamAndGroupTuple(teamNameNode.textValue(), groupRefNode.textValue());
            })
            .toList();
    }

    @NonNull
    private JsonNode queryResourceWithJq(String fieldName, GenericKubernetesResource resource, JsonQuery jqQuery, JsonNode resourceJson) {
        List<JsonNode> nodesResult = new ArrayList<>();
        try {
            jqQuery.apply(jqScope, resourceJson, nodesResult::add);
        } catch (JsonQueryException e) {
            throw new PropertyExtractionException(fieldName, jqQuery.toString(), resource, e);
        }
        if (nodesResult.size() != 1) {
            throw new PropertyExtractionException(fieldName, jqQuery.toString(), resource, "expected 1 result, got " + nodesResult.size());
        }
        JsonNode node = nodesResult.getFirst();
        if (node.isMissingNode()) {
            throw new PropertyExtractionException(fieldName, jqQuery.toString(), resource, "not found");
        }
        if (!node.isTextual()) {
            throw new PropertyExtractionException(fieldName, jqQuery.toString(), resource, "not a string");
        }
        return node;
    }

    @ConfigurationProperties("platform-integration.teams-scraping.k8s-generic.config")
    public record Config(
        String apiVersion,
        String apiGroup,
        String kind,
        @Nullable String namespace,
        Filter filter,
        JqExpression teamName,
        JqExpression groupRef
    ) {
    }

    public record Filter(
        @Nullable String nameRegexp,
        @Nullable String labelSelector
    ) {
    }

    public record JqExpression(
        String jqExpression
    ) {
        public JsonQuery compile() {
            try {
                return JsonQuery.compile(jqExpression, jqVersion);
            } catch (JsonQueryException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class PropertyExtractionException extends RuntimeException {
        private final String propertyType;
        private final String jqExpression;
        private final GenericKubernetesResource resource;

        public PropertyExtractionException(String propertyType, String jqExpression, GenericKubernetesResource resource, String message) {
            super(message);
            this.propertyType = propertyType;
            this.jqExpression = jqExpression;
            this.resource = resource;
        }

        public PropertyExtractionException(String propertyType, String jqExpression, GenericKubernetesResource resource, Throwable cause) {
            super(cause);
            this.propertyType = propertyType;
            this.jqExpression = jqExpression;
            this.resource = resource;
        }

        @Override
        public String getMessage() {
            return format(
                "Couldn't extract %s from %s using jq expression '%s': %s",
                propertyType,
                resource.getKind() + "." + resource.getApiVersion() + ":" + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName(),
                jqExpression,
                super.getMessage()
            );
        }
    }
}
