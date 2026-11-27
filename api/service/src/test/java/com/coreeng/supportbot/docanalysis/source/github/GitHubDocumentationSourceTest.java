package com.coreeng.supportbot.docanalysis.source.github;

import static com.coreeng.supportbot.docanalysis.source.github.TarGz.archive;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DocumentationPage;
import com.coreeng.supportbot.docanalysis.DocumentationSession;
import com.coreeng.supportbot.docanalysis.OpenResult;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

class GitHubDocumentationSourceTest {

    private static final String REPOSITORY = "example-org/docs";
    private static final Instant FETCHED_AT = Instant.parse("2026-11-02T09:00:00Z");
    private static final String TEN_TOKENS = "x".repeat(35);

    @TempDir
    Path temp;

    private final GitHubClient gitHub = mock(GitHubClient.class);
    private final Logger sourceLogger = (Logger) LoggerFactory.getLogger(GitHubDocumentationSource.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

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
                        .contains("documentation-analysis.source.github.context"));
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
        DocumentationCopy copy = new DocumentationCopy(gitHub, settings, Clock.fixed(FETCHED_AT, ZoneOffset.UTC));
        return new GitHubDocumentationSource(copy, settings, maxInputTokens);
    }

    private DocumentationAnalysisProps.GitHub settings(
            String repository, String branch, String path, List<String> fileExtensions) {
        return new DocumentationAnalysisProps.GitHub(
                repository,
                branch,
                path,
                fileExtensions,
                temp.resolve("documentation-analysis").toString(),
                Duration.ofHours(24),
                DataSize.ofKilobytes(100),
                100,
                "whole",
                2);
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
}
