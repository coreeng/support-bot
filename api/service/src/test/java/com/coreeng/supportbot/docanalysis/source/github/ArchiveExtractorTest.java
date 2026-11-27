package com.coreeng.supportbot.docanalysis.source.github;

import static com.coreeng.supportbot.docanalysis.source.github.TarGz.TOP;
import static com.coreeng.supportbot.docanalysis.source.github.TarGz.archive;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveExtractorTest {

    @TempDir
    Path temp;

    @Test
    void keepsOnlyFilesUnderThePathWithAConfiguredExtension() throws IOException {
        byte[] tarGz = archive()
                .file("README.md", "readme")
                .file("docs/index.md", "index")
                .file("docs/billing/invoices.MDX", "invoices")
                .file("docs/billing/diagram.png", "png")
                .file("docsite/other.md", "other")
                .file("src/docs/nested.md", "nested")
                .build();

        int kept = extract(new ArchiveExtractor("docs", List.of(".md", ".mdx"), 1_000, 10), tarGz);

        assertThat(kept).isEqualTo(2);
        assertThat(filesIn(copy())).containsExactlyInAnyOrder("index.md", "billing/invoices.MDX");
        assertThat(copy().resolve("index.md")).hasContent("index");
    }

    @Test
    void keepsTheWholeRepositoryWhenThePathIsTheRoot() throws IOException {
        byte[] tarGz = archive()
                .file("README.md", "readme")
                .file("guides/start.md", "start")
                .build();

        extract(new ArchiveExtractor("", List.of(".md"), 1_000, 10), tarGz);

        assertThat(filesIn(copy())).containsExactlyInAnyOrder("README.md", "guides/start.md");
    }

    @Test
    void skipsASymbolicLink() throws IOException {
        byte[] tarGz = archive()
                .file("docs/index.md", "index")
                .symbolicLink("docs/passwd.md", "/etc/passwd")
                .symbolicLink("docs/up.md", "../../outside.md")
                .build();

        extract(extractor(), tarGz);

        assertThat(filesIn(copy())).containsExactly("index.md");
        assertThat(filesIn(temp)).containsExactly("copy/index.md");
    }

    @Test
    void skipsAHardLink() throws IOException {
        byte[] tarGz = archive()
                .file("docs/index.md", "index")
                .hardLink("docs/linked.md", TOP + "docs/index.md")
                .hardLink("docs/passwd.md", "/etc/passwd")
                .build();

        extract(extractor(), tarGz);

        assertThat(filesIn(copy())).containsExactly("index.md");
    }

    @Test
    void skipsAPathContainingDotDot() throws IOException {
        byte[] tarGz = archive()
                .file("docs/index.md", "index")
                .file("docs/../../escaped.md", "escaped")
                .file("docs/area/../../../../escaped.md", "escaped")
                .build();

        extract(extractor(), tarGz);

        assertThat(filesIn(copy())).containsExactly("index.md");
        assertThat(filesIn(temp)).containsExactly("copy/index.md");
    }

    @Test
    void skipsAnAbsolutePath() throws IOException {
        byte[] tarGz = archive()
                .file("docs/index.md", "index")
                .rawFile("/" + TOP + "docs/absolute.md", "absolute")
                .build();

        extract(extractor(), tarGz);

        assertThat(filesIn(copy())).containsExactly("index.md");
    }

    @Test
    void failsWhenTheKeptFilesExceedTheSizeLimit() {
        byte[] tarGz = archive().file("docs/a.md", 600).file("docs/b.md", 600).build();
        ArchiveExtractor extractor = new ArchiveExtractor("docs", List.of(".md"), 1_000, 10);

        assertThatThrownBy(() -> extract(extractor, tarGz))
                .isInstanceOf(ArchiveRejectedException.class)
                .hasMessageContaining("max-copy-size");
    }

    @Test
    void countsOnlyTheKeptFilesAgainstTheLimits() throws IOException {
        byte[] tarGz = archive()
                .file("assets/video.mp4", 5_000)
                .file("assets/a.md", "a")
                .file("assets/b.md", "b")
                .file("docs/index.md", "index")
                .build();

        int kept = extract(new ArchiveExtractor("docs", List.of(".md"), 1_000, 1), tarGz);

        assertThat(kept).isEqualTo(1);
    }

    @Test
    void failsWhenTheKeptFilesExceedTheFileLimit() {
        byte[] tarGz = archive()
                .file("docs/a.md", "a")
                .file("docs/b.md", "b")
                .file("docs/c.md", "c")
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor("docs", List.of(".md"), 1_000, 2);

        assertThatThrownBy(() -> extract(extractor, tarGz))
                .isInstanceOf(ArchiveRejectedException.class)
                .hasMessageContaining("max-files");
    }

    @Test
    void failsOnAnArchiveThatIsNotGzippedTar() {
        assertThatThrownBy(() -> extract(extractor(), "not an archive".getBytes(UTF_8)))
                .isInstanceOf(IOException.class);
    }

    private static ArchiveExtractor extractor() {
        return new ArchiveExtractor("docs", List.of(".md"), 1_000, 10);
    }

    private int extract(ArchiveExtractor extractor, byte[] tarGz) throws IOException {
        Files.createDirectories(copy());
        return extractor.extract(new ByteArrayInputStream(tarGz), copy());
    }

    private Path copy() {
        return temp.resolve("copy");
    }

    static List<String> filesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
                    .map(file -> directory.relativize(file).toString())
                    .toList();
        }
    }
}
