package com.coreeng.supportbot.docanalysis.source.github;

import static com.coreeng.supportbot.docanalysis.source.github.ArchiveExtractorTest.filesIn;
import static com.coreeng.supportbot.docanalysis.source.github.TarGz.archive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.util.unit.DataSize;

class DocumentationCopyTest {

    private static final String REPOSITORY = "example-org/docs";
    private static final Instant START = Instant.parse("2026-11-02T09:00:00Z");

    @TempDir
    Path temp;

    private final GitHubClient gitHub = mock(GitHubClient.class);
    private final MutableClock clock = new MutableClock(START);

    @Test
    void fetchesWhenNoCopyIsHeldAndExposesTheCommitAndFetchTime() throws IOException {
        headIs("c1", archive().file("docs/billing/invoices.md", "invoices").file("README.md", "readme"));

        HeldCopy copy = newCopy().refresh().orElseThrow();

        assertThat(copy.commit()).isEqualTo("c1");
        assertThat(copy.fetchedAt()).isEqualTo(START);
        assertThat(filesIn(copy.directory())).containsExactly("billing/invoices.md");
        assertThat(copy.directory().resolve("billing/invoices.md")).hasContent("invoices");
    }

    @Test
    void usesARecentCopyWithoutContactingGitHub() {
        headIs("c1", archive().file("docs/index.md", "index"));
        DocumentationCopy documentation = newCopy();
        HeldCopy fetched = documentation.refresh().orElseThrow();
        clock.advance(Duration.ofHours(3));

        assertThat(documentation.refresh()).contains(fetched);
        verify(gitHub, times(1)).getBranchHeadCommit(REPOSITORY, "main");
    }

    @Test
    void onlyUpdatesTheFetchTimeOfAnExpiredCopyWhenTheBranchHasNotMoved() {
        headIs("c1", archive().file("docs/index.md", "index"));
        DocumentationCopy documentation = newCopy();
        HeldCopy fetched = documentation.refresh().orElseThrow();
        clock.advance(Duration.ofHours(30));

        HeldCopy refreshed = documentation.refresh().orElseThrow();

        assertThat(refreshed.commit()).isEqualTo("c1");
        assertThat(refreshed.fetchedAt()).isEqualTo(START.plus(Duration.ofHours(30)));
        assertThat(refreshed.directory()).isEqualTo(fetched.directory());
        verify(gitHub, times(1)).downloadArchive(eq(REPOSITORY), anyString(), any());
    }

    @Test
    void replacesAnExpiredCopyWhenTheBranchHasMoved() throws IOException {
        headIs("c1", archive().file("docs/old.md", "old"));
        DocumentationCopy documentation = newCopy();
        HeldCopy first = documentation.refresh().orElseThrow();
        clock.advance(Duration.ofHours(30));
        headIs("c2", archive().file("docs/new.md", "new"));

        HeldCopy second = documentation.refresh().orElseThrow();

        assertThat(second.commit()).isEqualTo("c2");
        assertThat(filesIn(second.directory())).containsExactly("new.md");
        assertThat(first.directory()).doesNotExist();
        assertThat(documentation.current()).contains(second);
    }

    @Test
    void fallsBackToAnExpiredCopyWhenGitHubCannotBeReached() throws IOException {
        headIs("c1", archive().file("docs/index.md", "index"));
        DocumentationCopy documentation = newCopy();
        HeldCopy fetched = documentation.refresh().orElseThrow();
        clock.advance(Duration.ofHours(30));
        when(gitHub.getBranchHeadCommit(REPOSITORY, "main")).thenThrow(new GitHubApiException(0, "unreachable"));

        assertThat(documentation.refresh()).contains(fetched);
        assertThat(filesIn(fetched.directory())).containsExactly("index.md");
    }

    @Test
    void holdsNothingWhenGitHubCannotBeReachedAndNoCopyIsHeld() {
        when(gitHub.getBranchHeadCommit(REPOSITORY, "main")).thenThrow(new GitHubApiException(503, "unavailable"));

        DocumentationCopy documentation = newCopy();

        assertThat(documentation.refresh()).isEmpty();
        assertThat(documentation.current()).isEmpty();
    }

    @Test
    void keepsTheCopyHeldWhenTheNewOneIsOverTheSizeLimit() throws IOException {
        assertTheCopyHeldSurvives(archive().file("docs/huge.md", 2_000));
    }

    @Test
    void keepsTheCopyHeldWhenTheNewOneIsOverTheFileLimit() throws IOException {
        TarGz tooMany = archive();
        for (int i = 0; i < 4; i++) {
            tooMany.file("docs/page-" + i + ".md", "page");
        }
        assertTheCopyHeldSurvives(tooMany);
    }

    @Test
    void keepsTheCopyHeldWhenTheDownloadBreaksPartWay() throws IOException {
        headIs("c1", archive().file("docs/index.md", "index"));
        DocumentationCopy documentation = newCopy();
        HeldCopy held = documentation.refresh().orElseThrow();
        clock.advance(Duration.ofHours(30));
        byte[] next = archive().file("docs/a.md", 300).file("docs/b.md", 300).build();
        when(gitHub.getBranchHeadCommit(REPOSITORY, "main")).thenReturn("c2");
        when(gitHub.downloadArchive(eq(REPOSITORY), eq("c2"), any()))
                .thenAnswer(invocation ->
                        streamTo(invocation, new BreaksAfter(new ByteArrayInputStream(next), next.length / 2)));

        assertThat(documentation.refresh()).contains(held);
        assertOnlyTheHeldCopyIsOnDisk(held);
    }

    @Test
    void reusesTheCopyOnDiskAfterARestart() {
        headIs("c1", archive().file("docs/index.md", "index"));
        HeldCopy fetched = newCopy().refresh().orElseThrow();
        clock.advance(Duration.ofHours(1));

        DocumentationCopy restarted = newCopy();

        assertThat(restarted.current()).contains(fetched);
        assertThat(restarted.refresh()).contains(fetched);
        verify(gitHub, times(1)).getBranchHeadCommit(REPOSITORY, "main");
    }

    @Test
    void fetchesTheCurrentVersionAfterARestartThatLostTheCopy() throws IOException {
        headIs("c1", archive().file("docs/old.md", "old"));
        assertThat(newCopy().refresh()).isPresent();
        deleteCopyLocation();
        headIs("c2", archive().file("docs/new.md", "new"));

        DocumentationCopy restarted = newCopy();
        assertThat(restarted.current()).isEmpty();
        HeldCopy refetched = restarted.refresh().orElseThrow();

        assertThat(refetched.commit()).isEqualTo("c2");
        assertThat(filesIn(refetched.directory())).containsExactly("new.md");
    }

    @Test
    void doesNotReuseACopyMadeFromAnotherRepository() {
        headIs("c1", archive().file("docs/index.md", "index"));
        assertThat(newCopy().refresh()).isPresent();

        DocumentationCopy otherRepository = new DocumentationCopy(gitHub, settings("example-org/handbook"), clock);

        assertThat(otherRepository.current()).isEmpty();
    }

    @Test
    void doesNotContactGitHubToReportTheCopyHeld() {
        assertThat(newCopy().current()).isEmpty();
        verifyNoInteractions(gitHub);
    }

    private void assertTheCopyHeldSurvives(TarGz rejected) throws IOException {
        headIs("c1", archive().file("docs/index.md", "index"));
        DocumentationCopy documentation = newCopy();
        HeldCopy held = documentation.refresh().orElseThrow();
        clock.advance(Duration.ofHours(30));
        headIs("c2", rejected);

        assertThat(documentation.refresh()).contains(held);
        assertThat(newCopy().current()).contains(held);
        assertOnlyTheHeldCopyIsOnDisk(held);
    }

    private void assertOnlyTheHeldCopyIsOnDisk(HeldCopy held) throws IOException {
        assertThat(filesIn(held.directory())).containsExactly("index.md");
        try (var entries = Files.list(location())) {
            assertThat(entries.map(entry -> entry.getFileName().toString()))
                    .containsExactlyInAnyOrder(
                            "copy.properties", held.directory().getFileName().toString());
        }
    }

    private void headIs(String commit, TarGz archive) {
        byte[] bytes = archive.build();
        when(gitHub.getBranchHeadCommit(REPOSITORY, "main")).thenReturn(commit);
        when(gitHub.downloadArchive(eq(REPOSITORY), eq(commit), any()))
                .thenAnswer(invocation -> streamTo(invocation, new ByteArrayInputStream(bytes)));
    }

    /** As {@link GitHubClient#downloadArchive} does, a failure of the reader comes out unchecked. */
    private static Object streamTo(InvocationOnMock downloadArchive, InputStream archive) {
        try {
            return downloadArchive.<GitHubClient.ArchiveReader<?>>getArgument(2).read(archive);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DocumentationCopy newCopy() {
        return new DocumentationCopy(gitHub, settings(REPOSITORY), clock);
    }

    private DocumentationAnalysisProps.GitHub settings(String repository) {
        return new DocumentationAnalysisProps.GitHub(
                repository,
                "main",
                "docs",
                List.of(".md"),
                location().toString(),
                Duration.ofHours(24),
                DataSize.ofBytes(1_000),
                3,
                2);
    }

    private Path location() {
        return temp.resolve("documentation-analysis");
    }

    private void deleteCopyLocation() throws IOException {
        try (var paths = Files.walk(location())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static final class BreaksAfter extends FilterInputStream {
        private long remaining;

        BreaksAfter(InputStream in, long bytes) {
            super(in);
            this.remaining = bytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining-- <= 0) {
                throw new IOException("connection reset");
            }
            return super.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                throw new IOException("connection reset");
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            remaining -= Math.max(read, 0);
            return read;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
