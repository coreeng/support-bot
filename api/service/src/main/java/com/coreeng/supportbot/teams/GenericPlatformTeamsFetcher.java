package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.util.JsonMapper;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Slf4j
@RequiredArgsConstructor
public class GenericPlatformTeamsFetcher implements PlatformTeamsFetcher {
    private static final String rootObjectName = "resource";

    private final CelOptions celOptions = CelOptions.current()
        .enableOptionalSyntax(true)
        .build();
    private final CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder()
        .setOptions(celOptions)
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addLibraries(CelOptionalLibrary.INSTANCE)
        .addVar(rootObjectName, MapType.create(SimpleType.STRING, SimpleType.DYN))
        .build();
    private final CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder()
        .setOptions(celOptions)
        .addLibraries(CelOptionalLibrary.INSTANCE)
        .build();

    private final Config config;
    private final KubernetesClient k8sClient;
    private final JsonMapper jsonMapper;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        CompiledCelExpression groupRefProgram = config.groupRef().compile(celCompiler, celRuntime);
        CompiledCelExpression teamNameProgram = config.teamName().compile(celCompiler, celRuntime);

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
                @SuppressWarnings("unchecked")
                Map<String, Object> resourceMap = jsonMapper.getObjectMapper().convertValue(r, Map.class);
                String teamName = evaluateExpression("teamName", r, teamNameProgram, resourceMap);
                String groupRef = evaluateExpression("groupRef", r, groupRefProgram, resourceMap);

                return new TeamAndGroupTuple(teamName, groupRef);
            })
            .toList();
    }

    @NonNull
    private String evaluateExpression(
        String fieldName,
        GenericKubernetesResource resource,
        CompiledCelExpression compiledExpression,
        Map<String, Object> resourceMap
    ) {
        try {
            Object result = compiledExpression.program().eval(Map.of(rootObjectName, resourceMap));

            if (result instanceof Optional<?> optional) {
                result = optional.orElseThrow(() ->
                    new PropertyExtractionException(fieldName, compiledExpression.string(), resource, "optional result is empty")
                );
            }

            if (result == null) {
                throw new PropertyExtractionException(fieldName, compiledExpression.string(), resource, "result is null");
            }
            if (!(result instanceof String)) {
                throw new PropertyExtractionException(fieldName, compiledExpression.string(), resource,
                    "expected String or optional String result, got " + result.getClass().getSimpleName());
            }
            return (String) result;
        } catch (CelEvaluationException e) {
            throw new PropertyExtractionException(fieldName, compiledExpression.string(), resource, e);
        }
    }

    @ConfigurationProperties("platform-integration.teams-scraping.k8s-generic.config")
    public record Config(
        String apiVersion,
        String apiGroup,
        String kind,
        @Nullable String namespace,
        Filter filter,
        CelExpression teamName,
        CelExpression groupRef
    ) {
    }

    public record Filter(
        @Nullable String nameRegexp,
        @Nullable String labelSelector
    ) {
    }

    public record CelExpression(
        String celExpression
    ) {
        public CompiledCelExpression compile(CelCompiler celCompiler, CelRuntime celRuntime) {
            try {
                CelAbstractSyntaxTree ast = celCompiler.compile(celExpression).getAst();
                CelRuntime.Program program = celRuntime.createProgram(ast);
                return new CompiledCelExpression(program, celExpression);
            } catch (CelValidationException | CelEvaluationException e) {
                throw new RuntimeException("Failed to compile CEL expression: " + celExpression, e);
            }
        }
    }

    public record CompiledCelExpression(
        CelRuntime.Program program,
        String string
    ) {
    }

    public static class PropertyExtractionException extends RuntimeException {
        private final String propertyType;
        private final String celExpression;
        private final GenericKubernetesResource resource;

        public PropertyExtractionException(String propertyType, String celExpression, GenericKubernetesResource resource, String message) {
            super(message);
            this.propertyType = propertyType;
            this.celExpression = celExpression;
            this.resource = resource;
        }

        public PropertyExtractionException(String propertyType, String celExpression, GenericKubernetesResource resource, Throwable cause) {
            super(cause);
            this.propertyType = propertyType;
            this.celExpression = celExpression;
            this.resource = resource;
        }

        @Override
        public String getMessage() {
            return format(
                "Couldn't extract %s from %s using CEL expression '%s': %s",
                propertyType,
                resource.getKind() + "." + resource.getApiVersion() + ":" + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName(),
                celExpression,
                super.getMessage()
            );
        }
    }
}
