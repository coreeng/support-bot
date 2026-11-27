package com.coreeng.supportbot.docanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class StubDocumentationSource implements DocumentationSource {

    public static final String DEFAULT_VERSION = "stub-version-1";
    public static final String DEFAULT_FINGERPRINT = "stub-fingerprint-1";

    private @Nullable String unavailableReason;
    private String version = DEFAULT_VERSION;
    private String fingerprint = DEFAULT_FINGERPRINT;
    private ContextAnswer defaultAnswer = new ContextAnswer.Failed("no_canned_answer", true);
    private final Map<String, ContextAnswer> answersByThread = new HashMap<>();
    private final List<Request> requests = new ArrayList<>();

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

    public StubDocumentationSource version(String version) {
        this.version = version;
        return this;
    }

    public StubDocumentationSource fingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    @Override
    public OpenResult open() {
        if (unavailableReason != null) {
            return new OpenResult.Unavailable(unavailableReason);
        }
        String sessionVersion = version;
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
        return Optional.empty();
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
