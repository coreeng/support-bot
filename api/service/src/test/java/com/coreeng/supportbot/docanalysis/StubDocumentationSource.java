package com.coreeng.supportbot.docanalysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class StubDocumentationSource implements DocumentationSource {

    public static final String DEFAULT_VERSION = "stub-version-1";
    public static final String DEFAULT_FINGERPRINT = "stub-fingerprint-1";
    public static final Instant DEFAULT_REFRESHED_AT = Instant.parse("2026-01-05T09:00:00Z");

    private @Nullable String unavailableReason;
    private String version = DEFAULT_VERSION;
    private Instant refreshedAt = DEFAULT_REFRESHED_AT;
    private String fingerprint = DEFAULT_FINGERPRINT;
    private @Nullable SourceStatus status;
    private ContextAnswer defaultAnswer = new ContextAnswer.Failed("no_canned_answer", true);
    private final Map<String, ContextAnswer> answersByThread = new HashMap<>();
    private final List<Request> requests = new ArrayList<>();
    private int openCount;

    public record Request(String thread, int budget) {}

    public static StubDocumentationSource answering(ContextAnswer answer) {
        return new StubDocumentationSource().answer(answer);
    }

    public static StubDocumentationSource unavailable(String reason) {
        StubDocumentationSource source = new StubDocumentationSource();
        source.unavailableReason = reason;
        return source;
    }

    public StubDocumentationSource answer(ContextAnswer answer) {
        this.defaultAnswer = answer;
        return this;
    }

    public StubDocumentationSource answerFor(String thread, ContextAnswer answer) {
        answersByThread.put(thread, answer);
        return this;
    }

    /** The version, and the refresh time, that the next successful {@link #open()} takes. */
    public StubDocumentationSource version(String version, Instant refreshedAt) {
        this.version = version;
        this.refreshedAt = refreshedAt;
        return this;
    }

    public StubDocumentationSource version(String version) {
        return version(version, refreshedAt);
    }

    /** What the source holds before any run opens it, as after a restart with a kept copy. */
    public StubDocumentationSource holding(String version, Instant refreshedAt) {
        this.status = new SourceStatus(version, refreshedAt);
        return this;
    }

    public StubDocumentationSource fingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public int openCount() {
        return openCount;
    }

    @Override
    public OpenResult open() {
        openCount++;
        if (unavailableReason != null) {
            return new OpenResult.Unavailable(unavailableReason);
        }
        String sessionVersion = version;
        status = new SourceStatus(sessionVersion, refreshedAt);
        return new OpenResult.Opened(new DocumentationSession() {
            @Override
            public String version() {
                return sessionVersion;
            }

            @Override
            public ContextAnswer findContext(String thread, int budget) {
                requests.add(new Request(thread, budget));
                return answersByThread.getOrDefault(thread, defaultAnswer);
            }
        });
    }

    @Override
    public Optional<SourceStatus> status() {
        return Optional.ofNullable(status);
    }

    @Override
    public String fingerprint() {
        return fingerprint;
    }

    public static DocumentationPage page(String id, @Nullable String area, String text) {
        return new DocumentationPage(id, area, text);
    }

    /** Pages of a source without product areas. */
    public static ContextAnswer.Pages pages(DocumentationPage... pages) {
        return new ContextAnswer.Pages(List.of(pages), List.of(), List.of(), null);
    }

    public static DistilledQuestion question(String text, String... tags) {
        return new DistilledQuestion(text, List.of(tags));
    }
}
