package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Holds each id at most once, as the table's primary key does. */
final class InMemoryAsyncJobRepository implements AsyncJobRepository {

    private final Map<String, AsyncJob> jobs = new ConcurrentHashMap<>();

    @Override
    public boolean tryStartJob(String asyncId, String data) {
        return jobs.putIfAbsent(asyncId, new AsyncJob(asyncId, data, Instant.EPOCH)) == null;
    }

    @Override
    public void deleteJob(String asyncId) {
        jobs.remove(asyncId);
    }

    @Override
    public @Nullable AsyncJob findJob(String asyncId) {
        return jobs.get(asyncId);
    }
}
