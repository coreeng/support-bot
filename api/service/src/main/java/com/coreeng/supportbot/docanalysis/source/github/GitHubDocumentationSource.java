package com.coreeng.supportbot.docanalysis.source.github;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.DocumentationSourceType;
import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DocumentationPage;
import com.coreeng.supportbot.docanalysis.DocumentationSource;
import com.coreeng.supportbot.docanalysis.Fingerprint;
import com.coreeng.supportbot.docanalysis.OpenResult;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/** Answers from the copy of the documentation repository, returning every page for every thread. */
@Slf4j
final class GitHubDocumentationSource implements DocumentationSource {

    static final String NO_COPY = "The documentation could not be fetched from GitHub, and no earlier copy is held.";
    static final String UNREADABLE_COPY = "The copy of the documentation could not be read.";
    static final String NO_PAGES =
            "The documentation holds no pages: no file under the configured path has a configured extension.";

    private final DocumentationCopy copy;
    private final DocumentationAnalysisProps.GitHub settings;
    private final int maxInputTokens;
    private final String fingerprint;

    GitHubDocumentationSource(DocumentationCopy copy, DocumentationAnalysisProps.GitHub settings, int maxInputTokens) {
        this.copy = copy;
        this.settings = settings;
        this.maxInputTokens = maxInputTokens;
        List<String> parts = new ArrayList<>(List.of(
                DocumentationSourceType.GITHUB.configValue(),
                settings.selectedContext().configValue(),
                settings.repository(),
                settings.branch(),
                settings.path()));
        parts.addAll(settings.fileExtensions());
        this.fingerprint = Fingerprint.of(parts);
    }

    @Override
    public OpenResult open() {
        Optional<HeldCopy> refreshed = copy.refresh();
        if (refreshed.isEmpty()) {
            return new OpenResult.Unavailable(NO_COPY);
        }
        HeldCopy held = refreshed.get();
        List<DocumentationPage> pages;
        try {
            pages = readPages(held.directory());
        } catch (IOException | UncheckedIOException e) {
            log.atWarn().setCause(e).log("Could not read the documentation copy of commit {}", held.commit());
            return new OpenResult.Unavailable(UNREADABLE_COPY);
        }
        if (pages.isEmpty()) {
            return new OpenResult.Unavailable(NO_PAGES);
        }
        ContextAnswer.Pages whole = new ContextAnswer.Pages(
                pages,
                pages.stream()
                        .map(DocumentationPage::area)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList(),
                List.of(),
                null);
        int tokens = whole.estimatedTokens();
        if (tokens > maxInputTokens) {
            return new OpenResult.Unavailable("The documentation is estimated at " + tokens
                    + " tokens, more than documentation-analysis.max-input-tokens (" + maxInputTokens + ")."
                    + " With documentation-analysis.source.github.context set to "
                    + settings.selectedContext().configValue()
                    + ", every page is sent with every ticket, so the documentation has to fit within that limit.");
        }
        return new OpenResult.Opened(new WholeDocumentationSession(held.commit(), whole));
    }

    @Override
    public Optional<SourceStatus> status() {
        return copy.current().map(held -> new SourceStatus(held.commit(), held.fetchedAt()));
    }

    @Override
    public String fingerprint() {
        return fingerprint;
    }

    private static List<DocumentationPage> readPages(Path directory) throws IOException {
        List<String> ids;
        try (Stream<Path> files = Files.walk(directory)) {
            ids = files.filter(Files::isRegularFile)
                    .map(file -> directory.relativize(file).toString())
                    .sorted()
                    .toList();
        }
        List<DocumentationPage> pages = new ArrayList<>();
        int leftOut = 0;
        for (String id : ids) {
            if (!DocumentationPage.isValidId(id)) {
                leftOut++;
                continue;
            }
            int slash = id.indexOf('/');
            String area = slash < 0 ? null : id.substring(0, slash);
            // Decoded leniently: one file that is not valid UTF-8 should not make the source unavailable.
            String text = new String(Files.readAllBytes(directory.resolve(id)), UTF_8);
            pages.add(new DocumentationPage(id, area, text));
        }
        if (leftOut > 0) {
            log.atWarn()
                    .log(
                            "Left out {} documentation files whose paths hold a quote, angle bracket or control character",
                            leftOut);
        }
        return pages;
    }
}
