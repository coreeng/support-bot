package com.coreeng.supportbot.docanalysis.source.github;

import static com.coreeng.supportbot.docanalysis.source.github.TarGz.archive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.llm.StubChatModel;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DistilledQuestion;
import com.coreeng.supportbot.docanalysis.DocumentationPage;
import com.coreeng.supportbot.docanalysis.DocumentationSession;
import com.coreeng.supportbot.docanalysis.OpenResult;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

class GitHubDocumentationSourceTest {

    private static final String REPOSITORY = "example-org/docs";
    private static final Instant FETCHED_AT = Instant.parse("2026-11-02T09:00:00Z");
    private static final String TEN_TOKENS = "x".repeat(35);
    private static final DistilledQuestion QUESTION =
            new DistilledQuestion("How does a tenant pay an invoice?", List.of("invoices", "payment"));
    private static final String REASON = "Paying is documented under billing.";

    @TempDir
    Path temp;

    private final GitHubClient gitHub = mock(GitHubClient.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private FakeChatModel model = new FakeChatModel();
    private final Logger sourceLogger = (Logger) LoggerFactory.getLogger(GitHubDocumentationSource.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void routingPromptInUse() {
        routingPromptIs("Route the thread to at most {{max_areas}} areas.");
    }

    @BeforeEach
    void captureLogs() {
        logged.start();
        sourceLogger.addAppender(logged);
    }

    @AfterEach
    void releaseLogs() {
        sourceLogger.detachAppender(logged);
    }

    @Test
    void returnsEveryPageInPathOrderWithTheAreaOfItsTopLevelDirectory() {
        headIs(
                "c1",
                archive()
                        .file("docs/network/ingress.md", "ingress")
                        .file("docs/index.md", "welcome")
                        .file("docs/billing/invoices/overdue.md", "overdue")
                        .file("docs/billing/refunds.md", "refunds")
                        .file("README.md", "not documentation"));

        ContextAnswer answer = open(source()).findContext("[tenant] how do I pay?", 1_000);

        assertThat(answer).isInstanceOfSatisfying(ContextAnswer.Pages.class, pages -> {
            assertThat(pages.pages())
                    .containsExactly(
                            new DocumentationPage("billing/invoices/overdue.md", "billing", "overdue"),
                            new DocumentationPage("billing/refunds.md", "billing", "refunds"),
                            new DocumentationPage("index.md", null, "welcome"),
                            new DocumentationPage("network/ingress.md", "network", "ingress"));
            assertThat(pages.areasSupplied()).containsExactly("billing", "network");
            assertThat(pages.areasNotSupplied()).isEmpty();
            assertThat(pages.question()).isNull();
        });
    }

    @Test
    void givesEveryThreadTheSameAnswerWithoutContactingGitHub() {
        headIs("c1", archive().file("docs/b.md", "b").file("docs/a.md", "a"));
        DocumentationSession session = open(source());
        clearInvocations(gitHub);

        ContextAnswer first = session.findContext("[tenant] how do I deploy?", 1_000);
        ContextAnswer second = session.findContext("[tenant] what is a tenant?", 1_000);

        assertThat(second).isEqualTo(first);
        verifyNoInteractions(gitHub);
    }

    @Test
    void isAvailableWhenEveryPageSitsDirectlyInTheConfiguredPath() {
        headIs("c1", archive().file("docs/install.md", "install").file("docs/faq.md", "faq"));

        ContextAnswer answer = open(source()).findContext("[tenant] how do I install it?", 1_000);

        assertThat(answer).isInstanceOfSatisfying(ContextAnswer.Pages.class, pages -> {
            assertThat(pages.pages()).extracting(DocumentationPage::id).containsExactly("faq.md", "install.md");
            assertThat(pages.pages()).extracting(DocumentationPage::area).containsOnlyNulls();
            assertThat(pages.areasSupplied()).isEmpty();
        });
    }

    @Test
    void takesPageIdsRelativeToTheRepositoryRootWhenThePathIsEmpty() {
        headIs("c1", archive().file("guides/start.md", "start").file("index.md", "index"));

        ContextAnswer answer = open(source(settings(REPOSITORY, "main", "", List.of(".md"))))
                .findContext("[tenant] where do I start?", 1_000);

        assertThat(answer).isInstanceOfSatisfying(ContextAnswer.Pages.class, pages -> assertThat(pages.pages())
                .containsExactly(
                        new DocumentationPage("guides/start.md", "guides", "start"),
                        new DocumentationPage("index.md", null, "index")));
    }

    @Test
    void failsWhenThePagesExceedTheBudgetAndTryingAgainCannotHelp() {
        headIs("c1", archive().file("docs/a.md", TEN_TOKENS).file("docs/b.md", TEN_TOKENS));
        DocumentationSession session = open(source());

        assertThat(session.findContext("[tenant] question", 20)).isInstanceOf(ContextAnswer.Pages.class);
        assertThat(session.findContext("[tenant] question", 19))
                .isEqualTo(new ContextAnswer.Failed(ContextAnswer.Failed.CONTEXT_TOO_LARGE, false));
    }

    @Test
    void isUnavailableWhenTheDocumentationAloneExceedsTheInputLimit() {
        headIs("c1", archive().file("docs/a.md", TEN_TOKENS).file("docs/b.md", TEN_TOKENS));

        assertThat(source(19).open())
                .isInstanceOfSatisfying(OpenResult.Unavailable.class, unavailable -> assertThat(unavailable.reason())
                        .contains("documentation-analysis.max-input-tokens")
                        .contains("documentation-analysis.source.github.context")
                        .contains("routed"));
        assertThat(source(20).open()).isInstanceOf(OpenResult.Opened.class);
    }

    @Test
    void isUnavailableWithoutGitHubTextWhenNoCopyCanBeObtained() {
        when(gitHub.getBranchHeadCommit(REPOSITORY, "main"))
                .thenThrow(new GitHubApiException(401, "Bad credentials for token ghp_secret"));

        OpenResult result = source().open();

        assertThat(result).isEqualTo(new OpenResult.Unavailable(GitHubDocumentationSource.NO_COPY));
    }

    @Test
    void isUnavailableWhenTheCopyHoldsNoPages() {
        headIs("c1", archive().file("README.md", "outside the path").file("docs/diagram.png", "not a page"));

        assertThat(source().open()).isEqualTo(new OpenResult.Unavailable(GitHubDocumentationSource.NO_PAGES));
    }

    @Test
    void leavesOutFilesWhosePathCannotIdentifyAPageAndWarnsOnceWithTheirCount() {
        headIs(
                "c1",
                archive()
                        .file("docs/kept.md", "kept")
                        .file("docs/say \"hi\".md", "quote")
                        .file("docs/a<b>.md", "angle brackets")
                        .file("docs/tab\there.md", "control character"));
        ContextAnswer answer = open(source()).findContext("[tenant] question", 1_000);

        assertThat(answer).isInstanceOfSatisfying(ContextAnswer.Pages.class, pages -> assertThat(pages.pages())
                .extracting(DocumentationPage::id)
                .containsExactly("kept.md"));
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("Left out 3"));
    }

    @Test
    void reportsTheCommitAsTheVersion() {
        headIs("c1", archive().file("docs/a.md", "a"));

        assertThat(open(source()).version()).isEqualTo("c1");
    }

    @Test
    void reportsTheHeldCommitAndFetchTimeWithoutContactingGitHub() {
        GitHubDocumentationSource source = source();
        assertThat(source.status()).isEmpty();
        headIs("c1", archive().file("docs/a.md", "a"));
        open(source);
        clearInvocations(gitHub);

        assertThat(source.status()).contains(new SourceStatus("c1", FETCHED_AT));
        assertThat(source().status()).contains(new SourceStatus("c1", FETCHED_AT));
        verifyNoInteractions(gitHub);
    }

    @Test
    void keepsItsFingerprintAcrossCommitsAndInstances() {
        GitHubDocumentationSource source = source();
        String before = source.fingerprint();
        headIs("c1", archive().file("docs/a.md", "a"));
        open(source);

        assertThat(source.fingerprint()).isEqualTo(before);
        assertThat(source().fingerprint()).isEqualTo(before);
    }

    @Test
    void changesItsFingerprintWithTheRepositoryBranchPathOrFileExtensions() {
        String base =
                source(settings(REPOSITORY, "main", "docs", List.of(".md"))).fingerprint();

        assertThat(List.of(
                        settings("example-org/handbook", "main", "docs", List.of(".md")),
                        settings(REPOSITORY, "release", "docs", List.of(".md")),
                        settings(REPOSITORY, "main", "guides", List.of(".md")),
                        settings(REPOSITORY, "main", "docs", List.of(".md", ".mdx"))))
                .extracting(changed -> source(changed).fingerprint())
                .doesNotContain(base)
                .doesNotHaveDuplicates();
    }

    @Test
    void routesWithAnIndexOfEachPagesFrontmatterOrFirstHeadingThenTheThreadThenThePrompt() {
        headIs(
                "c1",
                archive()
                        .file("docs/index.md", "---\ntitle: Welcome\n---\nwelcome")
                        .file(
                                "docs/billing/invoices.md",
                                "---\ntitle: Invoices\ndescription: >\n  How invoices\n  are issued\n---\n# Other\n")
                        .file("docs/billing/refunds.md", "```sh\n# not a heading\n```\n# Refunds #\nbody")
                        .file("docs/network/ingress.md", "---\ntitle: [unclosed\n---\n## Ingress rules\nbody"));
        model.answer(routing(true, "billing"));

        open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(model.lastPrompt()).isEqualTo("""
                        <page_index>
                        ## billing
                        billing/invoices.md | Invoices | How invoices are issued
                        billing/refunds.md | Refunds |

                        ## network
                        network/ingress.md | Ingress rules |
                        </page_index>

                        <thread>
                        [tenant] how do I pay?
                        </thread>

                        Route the thread to at most 2 areas.""");
        assertThat(model.requests.getLast().responseFormat()).isNull();
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("Indexed 1 documentation pages"));
    }

    @Test
    void asksForSchemaOutputFromAClientThatSupportsIt() {
        model = new FakeChatModel(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        headIs("c1", areas());
        model.answer(routing(true, "billing"));

        open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        ResponseFormat format = model.requests.getLast().responseFormat();
        assertThat(format).isNotNull();
        assertThat(format.jsonSchema()).isNotNull();
    }

    @Test
    void returnsTheRootPagesAndEveryPageOfTheChosenAreasAndNamesTheOthers() {
        headIs("c1", areas());
        model.answer(routing(true, "network", "billing"));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(answer).isInstanceOfSatisfying(ContextAnswer.Pages.class, pages -> {
            assertThat(pages.pages())
                    .extracting(DocumentationPage::id)
                    .containsExactly("index.md", "billing/invoices.md", "billing/refunds.md", "network/ingress.md");
            assertThat(pages.areasSupplied()).containsExactly("network", "billing");
            assertThat(pages.areasNotSupplied()).containsExactly("security");
            assertThat(pages.question()).isEqualTo(QUESTION);
        });
    }

    @Test
    void reportsAThreadThatAsksNoDocumentationQuestionWithItsQuestionTagsAndReason() {
        headIs("c1", areas());
        model.answer(routing(false));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] the platform is down", 1_000);

        assertThat(answer).isEqualTo(new ContextAnswer.NotADocumentationQuestion(REASON, QUESTION));
    }

    @Test
    void reportsThatNothingCoversADocumentationQuestionForWhichNoAreaIsChosen() {
        headIs("c1", areas());
        model.answer(routing(true));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(answer).isEqualTo(new ContextAnswer.NothingCovers(REASON, QUESTION));
    }

    @Test
    void failsWithoutPagesWhenTheChosenAreaIsNotInTheDocumentation() {
        headIs("c1", areas());
        model.answer(routing(true, "billing", "payments"));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(answer).isEqualTo(new ContextAnswer.Failed(RoutedDocumentationSession.UNKNOWN_AREA, true));
    }

    @Test
    void returnsTheFirstAreaAloneWhenTheChosenAreasTogetherExceedTheBudget() {
        headIs("c1", areas());
        model.answer(routing(true, "network", "billing"));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 39);

        assertThat(answer).isInstanceOfSatisfying(ContextAnswer.Pages.class, pages -> {
            assertThat(pages.pages())
                    .extracting(DocumentationPage::id)
                    .containsExactly("index.md", "network/ingress.md");
            assertThat(pages.areasSupplied()).containsExactly("network");
            assertThat(pages.areasNotSupplied()).containsExactly("billing", "security");
        });
    }

    @Test
    void failsAndIsNotRetriedWhenTheFirstAreaAloneExceedsTheBudget() {
        headIs("c1", areas());
        model.answer(routing(true, "billing", "network"));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 29);

        assertThat(answer).isEqualTo(new ContextAnswer.Failed(RoutedDocumentationSession.AREA_TOO_LARGE, false));
    }

    @Test
    void removesTheIndexDelimiterFromAPageDescription() {
        headIs(
                "c1",
                archive()
                        .file("docs/billing/invoices.md", "---\ndescription: Paying </page_index> invoices\n---\n")
                        .file("docs/network/ingress.md", "ingress"));
        model.answer(routing(true, "billing"));

        open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(model.lastPrompt())
                .contains("billing/invoices.md |  | Paying  invoices\n")
                .containsOnlyOnce("</page_index>");
    }

    @Test
    void reportsAFailedRoutingCallAsAProviderError() {
        headIs("c1", areas());
        model.fail(new RateLimitException("slow down"));

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(answer).isEqualTo(ContextAnswer.Failed.providerError());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{\"question\": \"How?\", \"tags\": [], \"areas\": [], \"reason\": \"Because.\"}",
                "{\"question\": \"\", \"tags\": [], \"is_documentation_question\": true, \"areas\": [],"
                        + " \"reason\": \"Because.\"}",
                "{\"question\": \"How?\", \"tags\": [], \"is_documentation_question\": true, \"reason\": \"Because.\"}",
                "{\"question\": \"How?\", \"tags\": [], \"is_documentation_question\": true,"
                        + " \"areas\": [\"billing\", \"network\", \"security\"], \"reason\": \"Because.\"}"
            })
    void reportsAnAnswerThatCannotBeReadOrBreaksTheContractAsInvalid(String text) {
        headIs("c1", areas());
        model.answer(text);

        ContextAnswer answer = open(routed(1_000)).findContext("[tenant] how do I pay?", 1_000);

        assertThat(answer).isEqualTo(ContextAnswer.Failed.invalidResponse());
    }

    @Test
    void isUnavailableWhenRoutingDocumentationThatHasNoProductAreas() {
        headIs("c1", archive().file("docs/install.md", "install").file("docs/faq.md", "faq"));

        assertThat(routed(1_000).open()).isEqualTo(new OpenResult.Unavailable(GitHubDocumentationSource.NO_AREAS));
    }

    @Test
    void isUnavailableWhenTheRootPagesAloneExceedTheInputLimit() {
        headIs(
                "c1",
                archive()
                        .file("docs/index.md", TEN_TOKENS)
                        .file("docs/glossary.md", TEN_TOKENS)
                        .file("docs/billing/invoices.md", "invoices"));

        assertThat(routed(19).open())
                .isInstanceOfSatisfying(OpenResult.Unavailable.class, unavailable -> assertThat(unavailable.reason())
                        .contains("documentation-analysis.max-input-tokens (19)")
                        .contains("directly in the configured path"));
        assertThat(routed(20).open()).isInstanceOf(OpenResult.Opened.class);
    }

    @Test
    void warnsOfAnAreaThatWithTheRootPagesExceedsTheInputLimit() {
        headIs("c1", areas());

        assertThat(routed(29).open()).isInstanceOf(OpenResult.Opened.class);

        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .startsWith("Product area billing is estimated at 30 tokens")
                        .contains(RoutedDocumentationSession.AREA_TOO_LARGE));
    }

    @Test
    void canBeRunLocallyOnTheStubModel() {
        headIs("c1", areas());
        DocumentationSession session = open(source(settings("routed", 2), 1_000, new StubChatModel()));

        List<ContextAnswer> answers = IntStream.range(0, 20)
                .mapToObj(i -> session.findContext("[tenant] question number " + i, 1_000))
                .toList();

        assertThat(answers)
                .hasAtLeastOneElementOfType(ContextAnswer.Pages.class)
                .hasAtLeastOneElementOfType(ContextAnswer.NothingCovers.class)
                .hasAtLeastOneElementOfType(ContextAnswer.NotADocumentationQuestion.class)
                .doesNotHaveAnyElementsOfTypes(ContextAnswer.Failed.class);
    }

    @Test
    void changesItsFingerprintWhenTheWayOfFindingContextChanges() {
        assertThat(source(settings("routed", 2)).fingerprint())
                .isNotEqualTo(source(settings("whole", 2)).fingerprint());
    }

    @Test
    void changesItsFingerprintWhenRoutingWithTheRoutingPromptOrTheAreasPerTicket() {
        String base = source(settings("routed", 2)).fingerprint();
        String moreAreas = source(settings("routed", 3)).fingerprint();
        routingPromptIs("Route the thread to at most {{max_areas}} areas, most likely first.");

        assertThat(List.of(moreAreas, source(settings("routed", 2)).fingerprint()))
                .doesNotContain(base)
                .doesNotHaveDuplicates();
    }

    @Test
    void keepsItsFingerprintWithoutRoutingWhenTheRoutingPromptOrTheAreasPerTicketChange() {
        String base = source(settings("whole", 2)).fingerprint();
        routingPromptIs("Another routing prompt.");

        assertThat(source(settings("whole", 3)).fingerprint()).isEqualTo(base);
    }

    @Test
    void cannotGiveAFingerprintWhenRoutingWithNoRoutingPromptInUse() {
        when(analysisService.inUsePrompt(AnalysisPromptType.DOCUMENTATION_ROUTING))
                .thenReturn(null);

        assertThatThrownBy(() -> source(settings("routed", 2)).fingerprint())
                .isInstanceOf(AnalysisPromptLoadException.class);
        assertThat(source(settings("whole", 2)).fingerprint()).isNotBlank();
    }

    /** Root page of 10 tokens; billing of 20 over two pages; network and security of 10 each. */
    private static TarGz areas() {
        return archive()
                .file("docs/index.md", TEN_TOKENS)
                .file("docs/billing/refunds.md", TEN_TOKENS)
                .file("docs/billing/invoices.md", TEN_TOKENS)
                .file("docs/network/ingress.md", TEN_TOKENS)
                .file("docs/security/keys.md", TEN_TOKENS);
    }

    private static String routing(boolean documentationQuestion, String... areas) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("question", QUESTION.text());
        answer.put("tags", QUESTION.tags());
        answer.put("is_documentation_question", documentationQuestion);
        answer.put("areas", List.of(areas));
        answer.put("reason", REASON);
        try {
            return new ObjectMapper().writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void routingPromptIs(String prompt) {
        when(analysisService.inUsePrompt(AnalysisPromptType.DOCUMENTATION_ROUTING))
                .thenReturn(new AnalysisPrompt(1, prompt));
    }

    private DocumentationSession open(GitHubDocumentationSource source) {
        OpenResult result = source.open();
        assertThat(result).isInstanceOf(OpenResult.Opened.class);
        return ((OpenResult.Opened) result).session();
    }

    private GitHubDocumentationSource source() {
        return source(1_000);
    }

    private GitHubDocumentationSource source(int maxInputTokens) {
        return source(settings(REPOSITORY, "main", "docs", List.of(".md")), maxInputTokens);
    }

    private GitHubDocumentationSource source(DocumentationAnalysisProps.GitHub settings) {
        return source(settings, 1_000);
    }

    private GitHubDocumentationSource source(DocumentationAnalysisProps.GitHub settings, int maxInputTokens) {
        return source(settings, maxInputTokens, model);
    }

    private GitHubDocumentationSource source(
            DocumentationAnalysisProps.GitHub settings, int maxInputTokens, ChatModel chatModel) {
        DocumentationCopy copy = new DocumentationCopy(gitHub, settings, Clock.fixed(FETCHED_AT, ZoneOffset.UTC));
        return new GitHubDocumentationSource(copy, settings, maxInputTokens, chatModel, analysisService);
    }

    private GitHubDocumentationSource routed(int maxInputTokens) {
        return source(settings("routed", 2), maxInputTokens);
    }

    private DocumentationAnalysisProps.GitHub settings(
            String repository, String branch, String path, List<String> fileExtensions) {
        return settings(repository, branch, path, fileExtensions, "whole", 2);
    }

    private DocumentationAnalysisProps.GitHub settings(String context, int maxAreasPerTicket) {
        return settings(REPOSITORY, "main", "docs", List.of(".md"), context, maxAreasPerTicket);
    }

    private DocumentationAnalysisProps.GitHub settings(
            String repository,
            String branch,
            String path,
            List<String> fileExtensions,
            String context,
            int maxAreasPerTicket) {
        return new DocumentationAnalysisProps.GitHub(
                repository,
                branch,
                path,
                fileExtensions,
                temp.resolve("documentation-analysis").toString(),
                Duration.ofHours(24),
                DataSize.ofKilobytes(100),
                100,
                context,
                maxAreasPerTicket);
    }

    private void headIs(String commit, TarGz archive) {
        byte[] bytes = archive.build();
        when(gitHub.getBranchHeadCommit(eq(REPOSITORY), anyString())).thenReturn(commit);
        when(gitHub.downloadArchive(eq(REPOSITORY), eq(commit), any())).thenAnswer(invocation -> {
            try {
                return invocation.<GitHubClient.ArchiveReader<?>>getArgument(2).read(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static final class FakeChatModel implements ChatModel {

        private final Set<Capability> capabilities;
        private final List<ChatRequest> requests = new ArrayList<>();
        private String answer = "";
        private @Nullable RuntimeException failure;

        FakeChatModel(Capability... capabilities) {
            this.capabilities = Set.of(capabilities);
        }

        void answer(String answer) {
            this.answer = answer;
        }

        void fail(RuntimeException failure) {
            this.failure = failure;
        }

        String lastPrompt() {
            return ((UserMessage) requests.getLast().messages().getFirst()).singleText();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return capabilities;
        }
    }
}
