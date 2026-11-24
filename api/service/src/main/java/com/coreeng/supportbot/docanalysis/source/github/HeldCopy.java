package com.coreeng.supportbot.docanalysis.source.github;

import java.nio.file.Path;
import java.time.Instant;

/**
 * @param directory holds the kept files at their paths relative to the configured documentation
 *     path, and nothing else
 */
record HeldCopy(String commit, Instant fetchedAt, Path directory) {}
