package com.coreeng.supportbot.docanalysis.source.github;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The copy of the documentation repository the bot keeps under {@code copy-location}. Each fetch
 * writes a new {@code copy-*} directory; {@code copy.properties} names the one in use, and is
 * replaced only once that directory is complete. The copy survives a restart of the bot and is
 * reused while it was made from the same repository, branch, path and file extensions.
 */
@Slf4j
final class DocumentationCopy {

    private static final String POINTER = "copy.properties";
    private static final String COPY_PREFIX = "copy-";
    private static final String INCOMING = "incoming";

    private final GitHubClient gitHub;
    private final DocumentationAnalysisProps.GitHub settings;
    private final Clock clock;
    private final Path location;
    private final String origin;
    private final ArchiveExtractor extractor;
    private volatile @Nullable HeldCopy held;

    DocumentationCopy(GitHubClient gitHub, DocumentationAnalysisProps.GitHub settings, Clock clock) {
        this.gitHub = gitHub;
        this.settings = settings;
        this.clock = clock;
        this.location = Path.of(settings.copyLocation()).toAbsolutePath().normalize();
        this.origin = String.join(
                " ",
                settings.repository(),
                settings.branch(),
                settings.path(),
                String.join(",", settings.fileExtensions()));
        this.extractor = new ArchiveExtractor(
                settings.path(),
                settings.fileExtensions(),
                settings.maxCopySize().toBytes(),
                settings.maxFiles());
        this.held = load();
    }

    /** The copy held now. Never contacts GitHub. */
    Optional<HeldCopy> current() {
        return Optional.ofNullable(held);
    }

    /**
     * Fetches when no copy is held or it is older than {@code max-copy-age}, downloading only when
     * the branch has moved. When that fails, the copy held before is still used, however old.
     *
     * @return the copy to read from; empty only when none is held and the fetch failed
     */
    synchronized Optional<HeldCopy> refresh() {
        HeldCopy before = held;
        if (before != null && clock.instant().isBefore(before.fetchedAt().plus(settings.maxCopyAge()))) {
            return Optional.of(before);
        }
        try {
            held = fetch(before);
        } catch (GitHubApiException | ArchiveRejectedException | IOException | UncheckedIOException e) {
            if (before == null) {
                log.atWarn()
                        .setCause(e)
                        .log("Could not fetch the documentation from {}; no copy is held", settings.repository());
            } else {
                log.atWarn()
                        .setCause(e)
                        .log(
                                "Could not refresh the documentation from {}; using the copy of commit {} fetched at {}",
                                settings.repository(),
                                before.commit(),
                                before.fetchedAt());
            }
        }
        return Optional.ofNullable(held);
    }

    private HeldCopy fetch(@Nullable HeldCopy before) throws IOException {
        String commit = gitHub.getBranchHeadCommit(settings.repository(), settings.branch());
        Instant now = clock.instant();
        if (before != null && before.commit().equals(commit)) {
            writePointer(before.directory(), commit, now);
            return new HeldCopy(commit, now, before.directory());
        }
        Files.createDirectories(location);
        removeLeftovers(before);
        Path incoming = location.resolve(INCOMING);
        Files.createDirectory(incoming);
        try {
            int files = gitHub.downloadArchive(
                    settings.repository(), commit, archive -> extractor.extract(archive, incoming));
            Path directory = location.resolve(COPY_PREFIX + UUID.randomUUID());
            Files.move(incoming, directory, StandardCopyOption.ATOMIC_MOVE);
            try {
                writePointer(directory, commit, now);
            } catch (IOException e) {
                deleteRecursively(directory);
                throw e;
            }
            if (before != null) {
                deleteReplaced(before.directory());
            }
            log.atInfo().log(
                    "Fetched {} documentation files from {} at commit {}", files, settings.repository(), commit);
            return new HeldCopy(commit, now, directory);
        } finally {
            deleteRecursively(incoming);
        }
    }

    private @Nullable HeldCopy load() {
        Properties pointer = new Properties();
        try (InputStream in = Files.newInputStream(location.resolve(POINTER))) {
            pointer.load(in);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            log.atWarn().setCause(e).log("Could not read the documentation copy in {}; it will be fetched", location);
            return null;
        }
        String name = pointer.getProperty("directory", "");
        String commit = pointer.getProperty("commit", "");
        Path directory = location.resolve(name);
        if (!origin.equals(pointer.getProperty("origin"))
                || !name.startsWith(COPY_PREFIX)
                || name.contains("/")
                || commit.isBlank()
                || !Files.isDirectory(directory)) {
            return null;
        }
        try {
            return new HeldCopy(commit, Instant.parse(pointer.getProperty("fetched-at", "")), directory);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private void writePointer(Path directory, String commit, Instant fetchedAt) throws IOException {
        Properties pointer = new Properties();
        pointer.setProperty("origin", origin);
        pointer.setProperty("directory", directory.getFileName().toString());
        pointer.setProperty("commit", commit);
        pointer.setProperty("fetched-at", fetchedAt.toString());
        Path written = location.resolve(POINTER + ".tmp");
        try (OutputStream out = Files.newOutputStream(written)) {
            pointer.store(out, null);
        }
        Files.move(written, location.resolve(POINTER), StandardCopyOption.ATOMIC_MOVE);
    }

    /** Removes what an interrupted fetch or a copy that failed to delete left behind. */
    private void removeLeftovers(@Nullable HeldCopy before) throws IOException {
        deleteRecursively(location.resolve(INCOMING));
        try (DirectoryStream<Path> copies = Files.newDirectoryStream(location, COPY_PREFIX + "*")) {
            for (Path copy : copies) {
                if (before == null || !copy.equals(before.directory())) {
                    deleteRecursively(copy);
                }
            }
        }
    }

    /** The new copy is in use by now, so a failure here leaves a leftover rather than failing the fetch. */
    private static void deleteReplaced(Path directory) {
        try {
            deleteRecursively(directory);
        } catch (IOException e) {
            log.atWarn().setCause(e).log("Could not delete the replaced documentation copy {}", directory);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, @Nullable IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
