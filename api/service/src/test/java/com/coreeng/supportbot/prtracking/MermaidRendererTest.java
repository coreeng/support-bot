package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Golden-file test: the committed lifecycle diagram must equal what {@link MermaidRenderer} renders
 * from {@link PrLifecycle#TRANSITIONS}, so a transition change that forgets to regenerate the diagram
 * fails the build. Regenerate after intentional FSM changes with:
 *
 * <pre>./gradlew :service:test --tests '*MermaidRendererTest*' -Ddocker=true -DupdateGolden=true</pre>
 */
class MermaidRendererTest {

    private static final String RELATIVE = "docs/diagrams/pr-lifecycle.generated.md";

    @Test
    void committedDiagramMatchesRenderedTable() throws IOException {
        String generated = MermaidRenderer.render(PrLifecycle.TRANSITIONS);
        Path doc = locateDoc();

        if (Boolean.getBoolean("updateGolden")) {
            Path parent = doc.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(doc, docContent(generated));
        }

        String committed = fencedMermaid(Files.readString(doc));
        assertThat(committed)
                .as(
                        "Diagram drift — regenerate %s with -DupdateGolden=true after changing PrLifecycle.TRANSITIONS",
                        RELATIVE)
                .isEqualTo(generated);
    }

    /** Resolves the doc under the service module — the first ancestor with both {@code build.gradle.kts} and {@code docs/}. */
    private static Path locateDoc() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("build.gradle.kts")) && Files.isDirectory(dir.resolve("docs"))) {
                return dir.resolve(RELATIVE);
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate the service module (build.gradle.kts + docs/) above "
                + Path.of("").toAbsolutePath());
    }

    private static String docContent(String diagram) {
        return """
                # PR lifecycle FSM (generated)

                <!-- Generated from PrLifecycle.TRANSITIONS by MermaidRenderer — do not edit by hand. -->
                <!-- Regenerate: ./gradlew :service:test --tests '*MermaidRendererTest*' -Ddocker=true -DupdateGolden=true -->

                ```mermaid
                %s
                ```
                """.formatted(diagram);
    }

    private static String fencedMermaid(String doc) {
        String open = "```mermaid\n";
        int start = doc.indexOf(open);
        if (start < 0) {
            throw new IllegalStateException("No ```mermaid fenced block found in " + RELATIVE);
        }
        start += open.length();
        int end = doc.indexOf("\n```", start);
        if (end < 0) {
            throw new IllegalStateException("Unterminated ```mermaid fenced block in " + RELATIVE);
        }
        return doc.substring(start, end);
    }
}
