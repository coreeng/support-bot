package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.util.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Slf4j
@RequiredArgsConstructor
public class GenericPlatformTeamsFetcher implements PlatformTeamsFetcher {
    private final Config config;
    private final KubernetesClient k8sClient;
    private final JsonMapper jsonMapper;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        ListOptions listOptions = new ListOptions();
        if (Strings.isNotBlank(config.filter().labelSelector())) {
            listOptions.setLabelSelector(config.filter().labelSelector());
        }

        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
            .withVersion(config.apiVersion())
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
                JsonNode teamNameNode = resourceJson.at(config.teamName().pointer());
                if (teamNameNode.isMissingNode()) {
                    throw new PropertyExtractionException("teamName", config.teamName().pointer(), r, "not found");
                }
                if (!teamNameNode.isTextual()) {
                    throw new PropertyExtractionException("teamName", config.teamName().pointer(), r, "not a string");
                }

                JsonNode groupRefNode = resourceJson.at(config.groupRef().pointer());
                if (groupRefNode.isMissingNode()) {
                    throw new PropertyExtractionException("groupRef", config.groupRef().pointer(), r, "not found");
                }
                if (!groupRefNode.isTextual()) {
                    throw new PropertyExtractionException("groupRef", config.groupRef().pointer(), r, "not a string");
                }
                return new TeamAndGroupTuple(teamNameNode.textValue(), groupRefNode.textValue());
            })
            .toList();
    }

    @ConfigurationProperties("platform-integration.teams-scraping.k8s-generic.config")
    public record Config(
        String apiVersion,
        String kind,
        @Nullable String namespace,
        Filter filter,
        PropertyPointer teamName,
        PropertyPointer groupRef
    ) {
    }

    public record Filter(
        @Nullable String nameRegexp,
        @Nullable String labelSelector
    ) {
    }

    public record PropertyPointer(
        String pointer
    ) {
    }

    @AllArgsConstructor
    public static class PropertyExtractionException extends RuntimeException {
        private String propertyType;
        private String pointer;
        private GenericKubernetesResource resource;
        private String reason;

        @Override
        public String getMessage() {
            return format(
                "Couldn't extract %s from %s using json-pointer %s: %s",
                propertyType,
                resource.getKind() + "." + resource.getApiVersion() + ":" + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName(),
                pointer,
                reason
            );
        }
    }
}
