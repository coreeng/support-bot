package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PrMessageRenderer {

    private static final DateTimeFormatter DEADLINE_FMT =
            DateTimeFormatter.ofPattern("EEE dd MMM 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    // CelRuntime.Program is thread-safe for concurrent eval() calls (stateless AST interpreter).
    private record CompiledProgram(String expression, CelRuntime.Program program) {}

    private final Map<String, Map<MessageEvent, CompiledProgram>> programs;
    private final CelRuntime runtime;

    private final PrUrlResolver urlResolver;

    public PrMessageRenderer(PrTrackingProps props, PrUrlResolver urlResolver) {
        this.urlResolver = urlResolver;
        CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
                .addVar("pr_number", SimpleType.INT)
                .addVar("pr_url", SimpleType.STRING)
                .addVar("repo_name", SimpleType.STRING)
                .addVar("repo_url", SimpleType.STRING)
                .addVar("sla_duration", SimpleType.STRING)
                .addVar("sla_deadline", SimpleType.STRING)
                .addVar("owning_team", SimpleType.STRING)
                .addVar("provider", SimpleType.STRING)
                .build();
        this.runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

        Map<String, Map<MessageEvent, CompiledProgram>> compiled = new HashMap<>();
        for (PrTrackingProps.Repository repo : props.repositories()) {
            if (repo.messages() != null) {
                compiled.put(repo.name(), compileAll(compiler, repo.messages(), repo.name()));
            }
        }
        this.programs = Map.copyOf(compiled);
    }

    /**
     * Renders the configured CEL template for the given repo and event. Returns {@code null} when no
     * override is configured or when the configured expression fails evaluation at runtime (a warning
     * is logged in the latter case), so callers can fall back to the default message.
     */
    @Nullable public String render(String repoName, MessageEvent event, PrMessageContext ctx) {
        Map<MessageEvent, CompiledProgram> repoPrograms = programs.get(repoName.toLowerCase(Locale.ROOT));
        if (repoPrograms == null) {
            return null;
        }
        CompiledProgram compiled = repoPrograms.get(event);
        if (compiled == null) {
            return null;
        }
        try {
            Object result = compiled.program().eval(toVars(ctx));
            if (result instanceof String str) {
                return str;
            }
            log.atWarn()
                    .addArgument(event)
                    .addArgument(repoName)
                    .addArgument(compiled.expression())
                    .log("CEL expression for {} on {} returned a non-String (expression: {}); using default");
            return null;
        } catch (CelEvaluationException e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(event)
                    .addArgument(repoName)
                    .addArgument(compiled.expression())
                    .log("CEL evaluation failed for {} on {} (expression: {}); using default");
            return null;
        }
    }

    /** Returns true when a custom message template is configured for this repo and event. */
    public boolean hasOverride(String repoName, MessageEvent event) {
        Map<MessageEvent, CompiledProgram> repoPrograms = programs.get(repoName.toLowerCase(Locale.ROOT));
        return repoPrograms != null && repoPrograms.containsKey(event);
    }

    private Map<MessageEvent, CompiledProgram> compileAll(
            CelCompiler compiler, PrTrackingProps.Messages messages, String repoName) {
        Map<MessageEvent, CompiledProgram> map = new EnumMap<>(MessageEvent.class);
        for (MessageEvent event : MessageEvent.values()) {
            compile(compiler, event, event.extract(messages), repoName, map);
        }
        return map;
    }

    private void compile(
            CelCompiler compiler,
            MessageEvent event,
            @Nullable String expression,
            String repoName,
            Map<MessageEvent, CompiledProgram> target) {
        if (expression == null) {
            return;
        }
        try {
            CelAbstractSyntaxTree ast = compiler.compile(expression).getAst();
            target.put(event, new CompiledProgram(expression, runtime.createProgram(ast)));
        } catch (CelValidationException | CelEvaluationException e) {
            log.atError()
                    .setCause(e)
                    .addArgument(event)
                    .addArgument(repoName)
                    .addArgument(expression)
                    .log(
                            "Invalid CEL expression for {} on repo '{}' ({}); custom message disabled for this event — using default");
        }
    }

    private Map<String, Object> toVars(PrMessageContext ctx) {
        return Map.of(
                "pr_number",
                (long) ctx.prNumber(),
                "pr_url",
                ctx.prNumber() > 0 ? urlResolver.publicUrlFor(ctx.repoName(), ctx.prNumber()) : "",
                "repo_name",
                ctx.repoName(),
                "repo_url",
                urlResolver.repoUrl(ctx.repoName()),
                "sla_duration",
                ctx.sla() != null ? PrDetectionService.formatDuration(ctx.sla()) : "",
                "sla_deadline",
                ctx.slaDeadline() != null ? DEADLINE_FMT.format(ctx.slaDeadline()) : "",
                "owning_team",
                ctx.owningTeam(),
                "provider",
                ctx.provider().storageValue());
    }
}
