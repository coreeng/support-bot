package com.coreeng.supportbot.docanalysis.source.github;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/** Builds archives shaped like GitHub's: every entry under one top-level directory. */
final class TarGz {

    static final String TOP = "example-org-docs-c0ffee/";

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final TarArchiveOutputStream tar;

    private TarGz() {
        try {
            tar = new TarArchiveOutputStream(new GZIPOutputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        directory(TOP);
    }

    static TarGz archive() {
        return new TarGz();
    }

    TarGz file(String path, String content) {
        return file(new TarArchiveEntry(TOP + path), content.getBytes(UTF_8));
    }

    TarGz file(String path, int size) {
        return file(new TarArchiveEntry(TOP + path), new byte[size]);
    }

    TarGz rawFile(String name, String content) {
        return file(new TarArchiveEntry(name, true), content.getBytes(UTF_8));
    }

    TarGz directory(String path) {
        return put(new TarArchiveEntry(path.startsWith(TOP) ? path : TOP + path, TarConstants.LF_DIR));
    }

    TarGz symbolicLink(String path, String target) {
        TarArchiveEntry entry = new TarArchiveEntry(TOP + path, TarConstants.LF_SYMLINK);
        entry.setLinkName(target);
        return put(entry);
    }

    TarGz hardLink(String path, String target) {
        TarArchiveEntry entry = new TarArchiveEntry(TOP + path, TarConstants.LF_LINK);
        entry.setLinkName(target);
        return put(entry);
    }

    byte[] build() {
        try {
            tar.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private TarGz file(TarArchiveEntry entry, byte[] content) {
        entry.setSize(content.length);
        try {
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    private TarGz put(TarArchiveEntry entry) {
        try {
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }
}
