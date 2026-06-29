package com.coreeng.supportbot.prtracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone, manually-run generator for the PR-lifecycle diagram: renders {@link
 * PrLifecycle#TRANSITIONS} through {@link MermaidRenderer} and writes the Markdown to the path passed
 * as its only argument.
 *
 * <p>Not a test and not wired into the build — the diagram is an optional, best-effort artefact and
 * nothing enforces that it stays in sync with the table. Refresh it after changing the FSM with {@code
 * make regen-fsm-diagram} (from {@code api/}), which runs the {@code regenPrLifecycleDiagram} Gradle
 * task. It lives in the test source set so it stays out of the shipped jar (and clear of Spring Boot's
 * single-{@code main} detection).
 */
public final class PrLifecycleDiagramGenerator {

    private PrLifecycleDiagramGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PrLifecycleDiagramGenerator <output-file>");
        }
        Path out = Path.of(args[0]);
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, docContent(MermaidRenderer.render(PrLifecycle.TRANSITIONS)));
    }

    private static String docContent(String diagram) {
        return """
                # PR lifecycle FSM (generated)

                <!-- Generated from PrLifecycle.TRANSITIONS by MermaidRenderer — do not edit by hand. -->
                <!-- Regenerate: make regen-fsm-diagram (from api/) -->

                ```mermaid
                %s
                ```
                """.formatted(diagram);
    }
}
