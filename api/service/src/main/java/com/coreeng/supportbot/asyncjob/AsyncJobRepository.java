package com.coreeng.supportbot.asyncjob;

import org.jspecify.annotations.Nullable;

public interface AsyncJobRepository {

    /**
     * Attempts to start a new batch. Returns true if successful, false if batch already exists.
     */
    boolean tryStartJob(String asyncId, String data);

    /** Deletes the batch record. */
    void deleteJob(String asyncId);

    /** Finds an existing batch by ID. */
    AsyncJobRepository.@Nullable AsyncJob findJob(String asyncId);

    record AsyncJob(String id, String data, java.time.Instant startedAt) {}
}
