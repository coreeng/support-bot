package com.coreeng.supportbot.teams;

import static java.lang.String.format;

import com.coreeng.supportbot.teams.groups.GroupRef;
import com.coreeng.supportbot.teams.groups.GroupRefParseException;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@RequiredArgsConstructor
public class GenericPlatformTeamsFetcher implements PlatformTeamsFetcher {
    private static final String ROOT_OBJECT_NAME = "resource";

    private final CelOptions celOptions =
            CelOptions.current().enableOptionalSyntax(true).build();
    private final CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(celOptions)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addLibraries(CelOptionalLibrary.INSTANCE)
            .addVar(ROOT_OBJECT_NAME, MapType.create(SimpleType.STRING, SimpleType.DYN))
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
        ImmutableMap<String, CompiledCelExpression> attributePrograms = compileAttributes();

        ListOptions listOptions = new ListOptions();
        if (Strings.isNotBlank(config.filter().labelSelector())) {
            listOptions.setLabelSelector(config.filter().labelSelector());
        }

        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                .withVersion(config.apiVersion())
                .withGroup(config.apiGroup())
                .withKind(config.kind())
                .withPlural(config.kind().toLowerCase(Locale.ROOT) + "s")
                .withNamespaced(Strings.isNotBlank(config.namespace()))
                .build();
        GenericKubernetesResourceList resourceList =
                k8sClient.genericKubernetesResources(context).list(listOptions);

        List<GenericKubernetesResource> items;
        if (Strings.isNotBlank(config.filter().nameRegexp())) {
            Pattern namePattern = Pattern.compile(config.filter().nameRegexp());
            items = resourceList.getItems().stream()
                    .filter(i -> namePattern.matcher(i.getMetadata().getName()).matches())
                    .toList();
        } else {
            items = resourceList.getItems();
        }

        ImmutableList.Builder<TeamAndGroupTuple> teams = ImmutableList.builderWithExpectedSize(items.size());
        Multiset<String> attributeFailures = HashMultiset.create();
        for (GenericKubernetesResource item : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resourceMap = jsonMapper.getObjectMapper().convertValue(item, Map.class);
            try {
                String teamName = evaluateExpression("teamName", item, teamNameProgram, resourceMap);
                String groupRefStr = evaluateExpression("groupRef", item, groupRefProgram, resourceMap);

                TeamAndGroupTuple apply = new TeamAndGroupTuple(
                        teamName,
                        teamName,
                        GroupRef.parse(groupRefStr),
                        evaluateAttributes(item, attributePrograms, resourceMap, attributeFailures));
                teams.add(apply);
            } catch (PropertyExtractionException e) {
                log.atWarn().setCause(e).log("Failed to extract teamName or groupRef. Skipping the team");
            } catch (GroupRefParseException e) {
                log.atWarn().setCause(e).log("Malformed groupRef from CEL expression. Skipping the team");
            }
        }
        ImmutableList<TeamAndGroupTuple> result = teams.build();
        logAttributeSummary(result, attributePrograms.keySet(), attributeFailures);
        return result;
    }

    /** Like teamName and groupRef, an attribute expression that doesn't compile fails the fetch. */
    private ImmutableMap<String, CompiledCelExpression> compileAttributes() {
        ImmutableMap.Builder<String, CompiledCelExpression> programs = ImmutableMap.builder();
        Map<String, CelExpression> configured = config.attributes();
        if (configured != null) {
            configured.forEach((name, expression) -> {
                try {
                    programs.put(name, expression.compile(celCompiler, celRuntime));
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Invalid CEL expression for team attribute '" + name + "'", e);
                }
            });
        }
        return programs.buildOrThrow();
    }

    /**
     * Attributes are optional extras: one that can't be extracted from a resource is left out and the team is kept.
     * An expression using optional syntax that yields no value, or a blank value, leaves the attribute out
     * without a warning. Only an attribute's first failure is logged with its cause; {@link
     * #logAttributeSummary} reports the totals.
     */
    private ImmutableMap<String, String> evaluateAttributes(
            GenericKubernetesResource resource,
            ImmutableMap<String, CompiledCelExpression> attributePrograms,
            Map<String, Object> resourceMap,
            Multiset<String> failures) {
        ImmutableMap.Builder<String, String> attributes = ImmutableMap.builder();
        attributePrograms.forEach((name, program) -> {
            try {
                evaluateAttribute(name, resource, program, resourceMap).ifPresent(value -> attributes.put(name, value));
            } catch (PropertyExtractionException e) {
                boolean firstFailure = failures.add(name, 1) == 0;
                log.atLevel(firstFailure ? Level.WARN : Level.DEBUG)
                        .setCause(e)
                        .addKeyValue("attribute", name)
                        .log("Failed to extract a team attribute. Leaving it unset");
            }
        });
        return attributes.buildOrThrow();
    }

    /**
     * One line per attribute, so an expression that is wrong for every resource stands out: with optional syntax
     * a mistyped path yields nothing rather than an error.
     */
    private void logAttributeSummary(
            ImmutableList<TeamAndGroupTuple> teams, Set<String> attributeNames, Multiset<String> failures) {
        for (String name : attributeNames) {
            long set =
                    teams.stream().filter(t -> t.attributes().containsKey(name)).count();
            int failed = failures.count(name);
            boolean suspicious = failed > 0 || (set == 0 && !teams.isEmpty());
            log.atLevel(suspicious ? Level.WARN : Level.INFO)
                    .addKeyValue("attribute", name)
                    .log(
                            "Team attribute '{}' set on {} of {} resources; failed on {}",
                            name,
                            set,
                            teams.size(),
                            failed);
        }
    }

    @NonNull private String evaluateExpression(
            String fieldName,
            GenericKubernetesResource resource,
            CompiledCelExpression compiledExpression,
            Map<String, Object> resourceMap) {
        try {
            Object result = compiledExpression.program().eval(Map.of(ROOT_OBJECT_NAME, resourceMap));

            if (result instanceof Optional<?> optional) {
                result = optional.orElseThrow(() -> new PropertyExtractionException(
                        fieldName, compiledExpression.string(), resource, "optional result is empty"));
            }

            if (result == null) {
                throw new PropertyExtractionException(
                        fieldName, compiledExpression.string(), resource, "result is null");
            }
            if (!(result instanceof String)) {
                throw new PropertyExtractionException(
                        fieldName,
                        compiledExpression.string(),
                        resource,
                        "expected String or optional String result, got "
                                + result.getClass().getSimpleName());
            }
            return (String) result;
        } catch (CelEvaluationException e) {
            throw new PropertyExtractionException(fieldName, compiledExpression.string(), resource, e);
        }
    }

    /**
     * Kept separate from {@link #evaluateExpression} so that teamName and groupRef behave exactly as before.
     *
     * @return the attribute's value, or empty when optional syntax yields no value or the value is blank
     * @throws PropertyExtractionException when evaluation fails or the result is not a String
     */
    private Optional<String> evaluateAttribute(
            String name,
            GenericKubernetesResource resource,
            CompiledCelExpression compiledExpression,
            Map<String, Object> resourceMap) {
        String fieldName = "attribute " + name;
        Object result;
        try {
            result = compiledExpression.program().eval(Map.of(ROOT_OBJECT_NAME, resourceMap));
        } catch (CelEvaluationException e) {
            throw new PropertyExtractionException(fieldName, compiledExpression.string(), resource, e);
        }

        // Two optional steps in a row, e.g. resource.metadata.?labels.?tier, yield an optional nested in
        // another. In cel-java a third step fails when the earlier fields are present (and yields empty
        // when they're not), so at most two are supported.
        while (result instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return Optional.empty();
            }
            result = optional.get();
        }
        if (!(result instanceof String value)) {
            throw new PropertyExtractionException(
                    fieldName,
                    compiledExpression.string(),
                    resource,
                    "expected a String, got "
                            + (result == null ? "null" : result.getClass().getSimpleName()));
        }
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    @ConfigurationProperties("platform-integration.teams-scraping.k8s-generic.config")
    public record Config(
            String apiVersion,
            String apiGroup,
            String kind,
            @Nullable String namespace,
            Filter filter,
            CelExpression teamName,
            CelExpression groupRef,
            @Nullable Map<String, CelExpression> attributes) {}

    public record Filter(
            @Nullable String nameRegexp, @Nullable String labelSelector) {}

    public record CelExpression(String celExpression) {
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

    public record CompiledCelExpression(CelRuntime.Program program, String string) {}

    public static class PropertyExtractionException extends RuntimeException {
        private final String propertyType;
        private final String celExpression;
        private final GenericKubernetesResource resource;

        public PropertyExtractionException(
                String propertyType, String celExpression, GenericKubernetesResource resource, String message) {
            super(message);
            this.propertyType = propertyType;
            this.celExpression = celExpression;
            this.resource = resource;
        }

        public PropertyExtractionException(
                String propertyType, String celExpression, GenericKubernetesResource resource, Throwable cause) {
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
                    resource.getApiVersion() + "/" + resource.getKind() + ":"
                            + resource.getMetadata().getNamespace() + "/"
                            + resource.getMetadata().getName(),
                    celExpression,
                    super.getMessage());
        }
    }
}
