package com.coreeng.supportbot.docanalysis.source.github;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.DocumentationSourceType;
import com.coreeng.supportbot.config.GitHubSourceContext;
import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DocumentationPage;
import com.coreeng.supportbot.docanalysis.DocumentationSource;
import com.coreeng.supportbot.docanalysis.Fingerprint;
import com.coreeng.supportbot.docanalysis.OpenResult;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import dev.langchain4j.model.chat.ChatModel;
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

/**
 * Answers from the copy of the documentation repository: every page for every thread, or, when it
 * routes, the pages of the product areas a routing call chooses.
 */
@Slf4j
final class GitHubDocumentationSource implements DocumentationSource {

    static final String NO_COPY = "The documentation could not be fetched from GitHub, and no earlier copy is held.";
    static final String UNREADABLE_COPY = "The copy of the documentation could not be read.";
    static final String NO_PAGES =
            "The documentation holds no pages: no file under the configured path has a configured extension.";
    static final String NO_AREAS = "The documentation is not arranged in product areas: every page sits directly in"
            + " the configured path. With documentation-analysis.source.github.context set to routed, the pages have"
            + " to sit in directories under that path, one for each product area.";
    private static final String MAX_AREAS_PLACEHOLDER = "{{max_areas}}";

    private final DocumentationCopy copy;
    private final DocumentationAnalysisProps.GitHub settings;
    private final int maxInputTokens;
    private final ChatModel chatModel;
    private final AnalysisService analysisService;
    private final List<String> settingsFingerprint;

    GitHubDocumentationSource(
            DocumentationCopy copy,
            DocumentationAnalysisProps.GitHub settings,
            int maxInputTokens,
            ChatModel chatModel,
            AnalysisService analysisService) {
        this.copy = copy;
        this.settings = settings;
        this.maxInputTokens = maxInputTokens;
        this.chatModel = chatModel;
        this.analysisService = analysisService;
        List<String> parts = new ArrayList<>(List.of(
                DocumentationSourceType.GITHUB.configValue(),
                settings.selectedContext().configValue(),
                settings.repository(),
                settings.branch(),
                settings.path()));
        parts.addAll(settings.fileExtensions());
        this.settingsFingerprint = List.copyOf(parts);
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
        return switch (settings.selectedContext()) {
            case WHOLE -> openWhole(held, pages);
            case ROUTED -> openRouted(held, pages);
        };
    }

    private OpenResult openWhole(HeldCopy held, List<DocumentationPage> pages) {
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
                    + " With documentation-analysis.source.github.context set to whole, every page is sent with"
                    + " every ticket, so the documentation has to fit within that limit. Set it to routed to send"
                    + " only the product areas each ticket needs.");
        }
        return new OpenResult.Opened(new WholeDocumentationSession(held.commit(), whole));
    }

    private OpenResult openRouted(HeldCopy held, List<DocumentationPage> pages) {
        PageIndex index = PageIndex.of(pages);
        if (index.unreadableFrontmatter() > 0) {
            log.atWarn()
                    .log(
                            "Indexed {} documentation pages by their first heading: their frontmatter is not valid YAML",
                            index.unreadableFrontmatter());
        }
        if (index.areaNames().isEmpty()) {
            return new OpenResult.Unavailable(NO_AREAS);
        }
        int rootTokens = index.rootTokens();
        if (rootTokens > maxInputTokens) {
            return new OpenResult.Unavailable("The pages directly in the configured path are estimated at " + rootTokens
                    + " tokens, more than documentation-analysis.max-input-tokens (" + maxInputTokens + ")."
                    + " With documentation-analysis.source.github.context set to routed, they are sent with every"
                    + " ticket, so they have to fit within that limit.");
        }
        for (String area : index.areaNames()) {
            int tokens = rootTokens + index.areaTokens(area);
            if (tokens > maxInputTokens) {
                log.atWarn()
                        .log(
                                "Product area {} is estimated at {} tokens with the root pages, more than"
                                        + " documentation-analysis.max-input-tokens ({}); its tickets will fail with {}",
                                area,
                                tokens,
                                maxInputTokens,
                                RoutedDocumentationSession.AREA_TOO_LARGE);
            }
        }
        int maxAreas = settings.maxAreasPerTicket();
        return new OpenResult.Opened(new RoutedDocumentationSession(
                held.commit(),
                index,
                chatModel,
                () -> routingPrompt().replace(MAX_AREAS_PLACEHOLDER, Integer.toString(maxAreas)),
                maxAreas));
    }

    @Override
    public Optional<SourceStatus> status() {
        return copy.current().map(held -> new SourceStatus(held.commit(), held.fetchedAt()));
    }

    /** @throws AnalysisPromptLoadException when routing and no routing prompt is in use */
    @Override
    public String fingerprint() {
        if (settings.selectedContext() == GitHubSourceContext.WHOLE) {
            return Fingerprint.of(settingsFingerprint);
        }
        List<String> parts = new ArrayList<>(settingsFingerprint);
        parts.add(routingPrompt());
        parts.add(Integer.toString(settings.maxAreasPerTicket()));
        return Fingerprint.of(parts);
    }

    private String routingPrompt() {
        AnalysisPrompt prompt = analysisService.inUsePrompt(AnalysisPromptType.DOCUMENTATION_ROUTING);
        if (prompt == null) {
            throw new AnalysisPromptLoadException("No documentation routing prompt version is marked as in use");
        }
        return prompt.content();
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
