package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisRecord;
import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.summary.LlmSummaryService;
import com.coreeng.supportbot.summary.SummaryBreakdowns;
import com.coreeng.supportbot.summary.SummaryCount;
import com.coreeng.supportbot.summary.SummaryWindow;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Drives the stub through the <em>real</em> call sites rather than hand-built prompt strings.
 *
 * <p>That is deliberate. The stub decides what kind of answer to give from a delimiter those call
 * sites add, so a test that constructed the prompt itself would keep passing after someone changed
 * the delimiter — exactly the regression worth catching. Going through
 * {@link LlmAnalysisService} also exercises the production parser, which is the only thing that
 * decides whether stubbed output is usable at all.
 */
class StubChatModelTest {

    private static final SummaryWindow WINDOW = new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));

    private final StubChatModel stub = new StubChatModel();

    @Test
    void classificationOutputParsesThroughTheRealAnalysisParser() {
        AnalysisRecord record = analyse(1L, "Ticket ID-4242\nHow do I expose my service externally?");

        assertThat(record).isNotNull();
        assertThat(record.isValid())
                .as("stubbed classification must survive the production parser: %s", record)
                .isTrue();
        assertThat(record.ticketId()).isEqualTo(1);
        assertThat(record.driver()).isNotBlank();
        assertThat(record.category()).isNotBlank();
        assertThat(record.feature()).isNotBlank();
        assertThat(record.summary()).contains("Stubbed classification");
    }

    @Test
    void classificationIsDeterministicForTheSameThread() {
        // The summary cache keys on a fingerprint derived from the analysis rows, so a stub that
        // varied per call would invalidate the cache on every visit and re-run the summary forever.
        AnalysisRecord first = analyse(7L, "the same thread text");
        AnalysisRecord second = analyse(7L, "the same thread text");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void classificationSpreadsAcrossBucketsForDifferentThreads() {
        // A stub that answered identically every time would render every breakdown as one bar and
        // hide any grouping bug in the page it exists to demo.
        Set<String> drivers = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            AnalysisRecord record = analyse(i + 1L, "thread number " + i);
            drivers.add(record.driver());
            categories.add(record.category());
        }

        assertThat(drivers).hasSizeGreaterThan(1);
        assertThat(categories).hasSizeGreaterThan(1);
    }

    @Test
    void summaryCallGetsProseRatherThanAClassificationBlock() {
        LlmSummaryService summaryService = new LlmSummaryService(stub, analysisProps());

        String summary = summaryService.generate("Summarise the window.", breakdowns(), ImmutableList.of("Because."));

        assertThat(summary).isNotBlank();
        // The two shapes are mutually exclusive: prose must not carry the strict classification lines,
        // or the summary section would render a parser block as its briefing.
        assertThat(summary).doesNotContain("Primary Driver:").doesNotContain("Platform Feature:");
        assertThat(summary).contains("stub LLM provider");
    }

    @Test
    void makesNoNetworkCallAndNeedsNoCredentials() {
        // Nothing to assert about sockets directly; the meaningful guarantee is that the model is
        // constructible with no configuration at all and still answers.
        assertThat(new StubChatModel().chat("anything at all")).isNotBlank();
    }

    private AnalysisRecord analyse(long ticketId, String threadText) {
        ThreadService threadService = mock(ThreadService.class);
        when(threadService.getThreadAsText(anyString(), anyString())).thenReturn(threadText);
        AnalysisRecord record = new LlmAnalysisService(stub, threadService)
                .analyzeThread("C123456", "1234.5678", ticketId, "Classify this thread.");
        assertThat(record).as("the analysis service returned no record").isNotNull();
        return record;
    }

    private static AnalysisProps analysisProps() {
        AnalysisProps.Llm llm = new AnalysisProps.Llm(
                "stub-local",
                Duration.ofMillis(1),
                new AnalysisProps.Vertex(false, "", ""),
                new AnalysisProps.Proxy(false, "", new AnalysisProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new AnalysisProps.Stub(true));
        return new AnalysisProps(
                llm,
                new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip"),
                new AnalysisProps.Prompt(true));
    }

    private static SummaryBreakdowns breakdowns() {
        return new SummaryBreakdowns(
                WINDOW,
                3,
                2,
                ImmutableList.of(new SummaryCount("Knowledge Gap", 2)),
                ImmutableList.of(new SummaryCount("Build & CI", 2)),
                ImmutableList.of(new SummaryCount("deployment pipelines", 2)),
                ImmutableList.of(new SummaryCount("team-a", 3)));
    }
}
