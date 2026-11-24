package com.coreeng.supportbot.docanalysis.source.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * Writes the documentation files of a GitHub repository archive into a directory. GitHub puts every
 * entry under one top-level directory, which is dropped along with the documentation path.
 */
final class ArchiveExtractor {

    private final List<String> documentationPath;
    private final List<String> fileExtensions;
    private final long maxBytes;
    private final int maxFiles;

    /** @param documentationPath relative, without {@code .} or {@code ..} segments; empty for the root */
    ArchiveExtractor(String documentationPath, List<String> fileExtensions, long maxBytes, int maxFiles) {
        this.documentationPath = segments(documentationPath);
        this.fileExtensions = fileExtensions.stream()
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .toList();
        this.maxBytes = maxBytes;
        this.maxFiles = maxFiles;
    }

    /**
     * @param target an empty directory
     * @return the number of files written
     * @throws ArchiveRejectedException when the kept files exceed the size or file limit
     */
    int extract(InputStream gzippedTar, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        int files = 0;
        long bytes = 0;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(gzippedTar))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                Optional<Path> kept = keptPath(entry);
                if (kept.isEmpty()) {
                    continue;
                }
                files++;
                if (files > maxFiles) {
                    throw new ArchiveRejectedException(
                            "the documentation has more files than max-files allows (" + maxFiles + ")");
                }
                Path file = root.resolve(kept.get()).normalize();
                if (!file.startsWith(root)) {
                    throw new ArchiveRejectedException("an archive entry would fall outside the copy");
                }
                Files.createDirectories(file.getParent());
                bytes += write(tar, file, maxBytes - bytes);
            }
        }
        return files;
    }

    private Optional<Path> keptPath(TarArchiveEntry entry) {
        byte type = entry.getLinkFlag();
        if (type != TarConstants.LF_NORMAL && type != TarConstants.LF_OLDNORM) {
            return Optional.empty();
        }
        String name = entry.getName();
        if (name.startsWith("/") || name.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        List<String> segments = segments(name);
        if (segments.contains("..") || segments.size() < 2 + documentationPath.size()) {
            return Optional.empty();
        }
        List<String> inRepository = segments.subList(1, segments.size());
        if (!inRepository.subList(0, documentationPath.size()).equals(documentationPath)) {
            return Optional.empty();
        }
        List<String> relative = inRepository.subList(documentationPath.size(), inRepository.size());
        String fileName = relative.getLast().toLowerCase(Locale.ROOT);
        if (fileExtensions.stream().noneMatch(fileName::endsWith)) {
            return Optional.empty();
        }
        return Optional.of(Path.of(String.join("/", relative)));
    }

    private static long write(InputStream content, Path file, long allowance) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                written += read;
                if (written > allowance) {
                    throw new ArchiveRejectedException("the documentation is larger than max-copy-size");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    private static List<String> segments(String path) {
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty() && !segment.equals("."))
                .toList();
    }
}
