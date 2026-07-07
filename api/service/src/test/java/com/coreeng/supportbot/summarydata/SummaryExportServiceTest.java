package com.coreeng.supportbot.summarydata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelProps;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.summarydata.SummaryExportService.CompletedExport;
import com.coreeng.supportbot.summarydata.SummaryExportService.ExportStatus;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class SummaryExportServiceTest {

    @Mock
    private AsyncJobRepository asyncJobRepository;

    @Mock
    private ThreadService threadService;

    @Mock
    private ApplicationContext applicationContext;

    private SlackChannelRegistry channelRegistry;
    private SummaryExportService service;

    @BeforeEach
    void setUp() {
        channelRegistry = new SlackChannelRegistry(
                new SlackTicketsProps("C123", List.of(), "eyes", "eyes", "white_check_mark", "sos"));
        service = new SummaryExportService(asyncJobRepository, threadService, channelRegistry, applicationContext);
    }

    private static CompletedExport completedExport(
            String content, String filename, Instant completedAt, Instant expiresAt) {
        return new CompletedExport(content.getBytes(StandardCharsets.UTF_8), filename, 1, completedAt, expiresAt);
    }

    private static List<String> zipEntryNames(byte[] zipBytes) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    private static List<String> zipEntryContents(byte[] zipBytes) throws IOException {
        List<String> contents = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                contents.add(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return contents;
    }

    // --- resumeExportOnStartup: delete-without-resume ---

    @Test
    void resumeExportOnStartup_deletesStaleJobRow_withoutResuming() {
        AsyncJobRepository.AsyncJob staleJob = new AsyncJobRepository.AsyncJob("summary-export", "7", Instant.now());
        when(asyncJobRepository.findJob("summary-export")).thenReturn(staleJob);

        service.resumeExportOnStartup();

        verify(asyncJobRepository).findJob("summary-export");
        verify(asyncJobRepository).deleteJob("summary-export");
        verifyNoInteractions(applicationContext, threadService);
    }

    @Test
    void resumeExportOnStartup_doesNothingWhenNoJobExists() {
        when(asyncJobRepository.findJob("summary-export")).thenReturn(null);

        service.resumeExportOnStartup();

        verify(asyncJobRepository).findJob("summary-export");
        verify(asyncJobRepository, never()).deleteJob(anyString());
        verifyNoInteractions(applicationContext, threadService);
    }

    // --- start()/status ---

    @Test
    void start_shouldStartJobWhenNotRunning() {
        when(asyncJobRepository.tryStartJob("summary-export", "7")).thenReturn(true);
        when(applicationContext.getBean(SummaryExportService.class)).thenReturn(service);
        when(threadService.getThreadsWithCheckMarkAsText(anyString(), anyInt())).thenReturn(ImmutableList.of());

        boolean result = service.start(7);

        assertThat(result).isTrue();
        verify(asyncJobRepository).tryStartJob("summary-export", "7");
    }

    @Test
    void start_shouldReturnFalseWhenJobAlreadyRunning() {
        when(asyncJobRepository.tryStartJob("summary-export", "7")).thenReturn(false);

        boolean result = service.start(7);

        assertThat(result).isFalse();
        verifyNoInteractions(applicationContext);
    }

    @Test
    void start_shouldDeleteJobAndReturnFalse_whenExecutorRejectsTask() {
        when(asyncJobRepository.tryStartJob("summary-export", "7")).thenReturn(true);
        when(applicationContext.getBean(SummaryExportService.class))
                .thenThrow(new TaskRejectedException("Executor queue full"));

        boolean result = service.start(7);

        assertThat(result).isFalse();
        verify(asyncJobRepository).deleteJob("summary-export");
    }

    @Test
    void start_discardsPreviousCompletedExport_immediately() {
        service.currentExport.set(completedExport(
                "old content", "old-export.zip", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        when(asyncJobRepository.tryStartJob("summary-export", "7")).thenReturn(true);
        when(applicationContext.getBean(SummaryExportService.class)).thenReturn(service);
        when(threadService.getThreadsWithCheckMarkAsText(anyString(), anyInt())).thenReturn(ImmutableList.of());

        service.start(7);

        Optional<CompletedExport> result = service.currentServableExport();
        assertThat(result).isPresent();
        assertThat(result.get().displayFilename()).isNotEqualTo("old-export.zip");
    }

    @Test
    void start_clearsThePreviousExportReference_asItsOwnStep_independentOfWhatTheNewRunProduces() {
        // start_discardsPreviousCompletedExport_immediately (above) only proves the END state holds a
        // different export — but runAsyncExport unconditionally overwrites currentExport when it
        // succeeds, so that alone can't tell apart "the old export was actively discarded" from
        // "nothing discarded anything, the successful run just overwrote it anyway". Making the run
        // itself fail (so nothing overwrites the reference) isolates the discard step specifically.
        service.currentExport.set(completedExport(
                "old content", "old-export.zip", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        SlackChannelRegistry brokenRegistry = mock(SlackChannelRegistry.class);
        when(brokenRegistry.monitoredChannelIds()).thenThrow(new RuntimeException("registry unavailable"));
        SummaryExportService brokenService =
                new SummaryExportService(asyncJobRepository, threadService, brokenRegistry, applicationContext);
        brokenService.currentExport.set(completedExport(
                "old content", "old-export.zip", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        when(asyncJobRepository.tryStartJob("summary-export", "7")).thenReturn(true);
        when(applicationContext.getBean(SummaryExportService.class)).thenReturn(brokenService);

        brokenService.start(7);

        // The run failed (no new export produced), yet the old one is still gone — proof the discard
        // happened as its own step, not as a side effect of a successful new export overwriting it.
        assertThat(brokenService.currentExport.get()).isNull();
        assertThat(brokenService.getStatus().error()).isNotNull();
    }

    @Test
    void getStatus_shouldReturnIdleByDefault() {
        ExportStatus status = service.getStatus();

        assertThat(status.running()).isFalse();
        assertThat(status.startedAt()).isNull();
        assertThat(status.error()).isNull();
    }

    // --- currentServableExport / consumeCurrentExport: peek vs. single-consumption ---

    @Test
    void currentServableExport_returnsEmptyWhenNoneCompleted() {
        assertThat(service.currentServableExport()).isEmpty();
    }

    @Test
    void currentServableExport_returnsExport_whenNotYetExpired() {
        CompletedExport export = completedExport(
                "content", "export.zip", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        service.currentExport.set(export);

        assertThat(service.currentServableExport()).contains(export);
        // a peek must not consume it
        assertThat(service.currentServableExport()).contains(export);
    }

    @Test
    void currentServableExport_evictsAndReturnsEmpty_whenPastRetention() {
        CompletedExport export = completedExport(
                "content",
                "export.zip",
                Instant.now().minus(9, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        service.currentExport.set(export);

        assertThat(service.currentServableExport()).isEmpty();
        // eviction actually clears the reference, not just hides it from this one call
        assertThat(service.currentExport.get()).isNull();
    }

    @Test
    void consumeCurrentExport_returnsEmptyWhenNoneCompleted() {
        assertThat(service.consumeCurrentExport()).isEmpty();
    }

    @Test
    void consumeCurrentExport_returnsExport_thenClearsIt_soASecondCallGetsNothing() {
        CompletedExport export = completedExport(
                "content", "export.zip", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        service.currentExport.set(export);

        assertThat(service.consumeCurrentExport()).contains(export);
        // the reference itself is cleared, not just hidden from subsequent reads
        assertThat(service.currentExport.get()).isNull();
        assertThat(service.consumeCurrentExport()).isEmpty();
        assertThat(service.currentServableExport()).isEmpty();
    }

    @Test
    void consumeCurrentExport_returnsEmpty_whenPastRetention() {
        CompletedExport export = completedExport(
                "content",
                "export.zip",
                Instant.now().minus(9, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        service.currentExport.set(export);

        assertThat(service.consumeCurrentExport()).isEmpty();
    }

    // --- expireStaleExport: the active backstop sweep ---

    @Test
    void expireStaleExport_clearsExport_pastRetention() {
        CompletedExport export = completedExport(
                "content",
                "export.zip",
                Instant.now().minus(9, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        service.currentExport.set(export);

        service.expireStaleExport();

        assertThat(service.currentExport.get()).isNull();
    }

    @Test
    void expireStaleExport_leavesFreshExportInPlace() {
        CompletedExport export = completedExport(
                "content", "export.zip", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        service.currentExport.set(export);

        service.expireStaleExport();

        assertThat(service.currentExport.get()).isEqualTo(export);
    }

    @Test
    void expireStaleExport_doesNothingWhenNoneCompleted() {
        service.expireStaleExport();

        assertThat(service.currentExport.get()).isNull();
    }

    // --- runAsyncExport: real zip-writing logic ---

    @Test
    void runAsyncExport_writesZipWithThreadFiles_andPublishesCompletedExport() throws IOException {
        var threads = ImmutableList.of(
                new ThreadService.ThreadData("1700000000.000001", "Thread one content"),
                new ThreadService.ThreadData("1700000000.000002", "Thread two content"));
        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(threads);

        service.runAsyncExport(31);

        verify(asyncJobRepository).deleteJob("summary-export");
        assertThat(service.getStatus().running()).isFalse();
        assertThat(service.getStatus().error()).isNull();

        Optional<CompletedExport> export = service.currentServableExport();
        assertThat(export).isPresent();
        assertThat(export.get().threadCount()).isEqualTo(2);
        assertThat(export.get().displayFilename())
                .matches("downloaded-threads-\\d{4}-\\d{2}-\\d{2}-to-\\d{4}-\\d{2}-\\d{2}\\.zip");
        assertThat(export.get().expiresAt()).isAfter(export.get().completedAt());

        assertThat(zipEntryNames(export.get().content()))
                .containsExactly("1700000000.000001.txt", "1700000000.000002.txt");
        assertThat(zipEntryContents(export.get().content()))
                .containsExactly("Thread one content", "Thread two content");
    }

    @Test
    void runAsyncExport_skipsChannelThatFailsAndStillExportsOthers() throws IOException {
        SlackChannelRegistry multiChannel = new SlackChannelRegistry(new SlackTicketsProps(
                null,
                List.of(
                        new SlackChannelProps("a", "C-a", SlackChannelProps.TrackMode.BOTH),
                        new SlackChannelProps("b", "C-b", SlackChannelProps.TrackMode.BOTH)),
                "eyes",
                "eyes",
                "white_check_mark",
                "sos"));
        SummaryExportService multiService =
                new SummaryExportService(asyncJobRepository, threadService, multiChannel, applicationContext);

        when(threadService.getThreadsWithCheckMarkAsText("C-a", 31)).thenThrow(new RuntimeException("not_in_channel"));
        when(threadService.getThreadsWithCheckMarkAsText("C-b", 31))
                .thenReturn(ImmutableList.of(new ThreadService.ThreadData("1700000000.000009", "Channel B content")));

        multiService.runAsyncExport(31);

        assertThat(multiService.getStatus().error()).isNull();
        Optional<CompletedExport> export = multiService.currentServableExport();
        assertThat(export).isPresent();
        assertThat(zipEntryNames(export.get().content())).containsExactly("1700000000.000009.txt");
    }

    @Test
    void runAsyncExport_keepsBothThreadsWhenTimestampsCollideAcrossChannels() throws IOException {
        SlackChannelRegistry multiChannel = new SlackChannelRegistry(new SlackTicketsProps(
                null,
                List.of(
                        new SlackChannelProps("a", "C-a", SlackChannelProps.TrackMode.BOTH),
                        new SlackChannelProps("b", "C-b", SlackChannelProps.TrackMode.BOTH)),
                "eyes",
                "eyes",
                "white_check_mark",
                "sos"));
        SummaryExportService multiService =
                new SummaryExportService(asyncJobRepository, threadService, multiChannel, applicationContext);

        when(threadService.getThreadsWithCheckMarkAsText("C-a", 31))
                .thenReturn(ImmutableList.of(new ThreadService.ThreadData("1700000000.000001", "from A")));
        when(threadService.getThreadsWithCheckMarkAsText("C-b", 31))
                .thenReturn(ImmutableList.of(new ThreadService.ThreadData("1700000000.000001", "from B")));

        multiService.runAsyncExport(31);

        Optional<CompletedExport> export = multiService.currentServableExport();
        assertThat(export).isPresent();
        assertThat(zipEntryNames(export.get().content()))
                .containsExactlyInAnyOrder("1700000000.000001.txt", "1700000000.000001-2.txt");
        assertThat(zipEntryContents(export.get().content())).containsExactlyInAnyOrder("from A", "from B");
    }

    @Test
    void runAsyncExport_writesEmptyZip_whenNoThreadsFound() throws IOException {
        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(ImmutableList.of());

        service.runAsyncExport(31);

        Optional<CompletedExport> export = service.currentServableExport();
        assertThat(export).isPresent();
        assertThat(export.get().threadCount()).isEqualTo(0);
        assertThat(zipEntryNames(export.get().content())).isEmpty();
    }

    @Test
    void runAsyncExport_disambiguatesDuplicateFileNames_withoutFailing() throws IOException {
        var threads = ImmutableList.of(
                new ThreadService.ThreadData("1700000000.000001", "First content"),
                new ThreadService.ThreadData("1700000000.000001", "Duplicate content"),
                new ThreadService.ThreadData("1700000000.000002", "Second content"));
        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(threads);

        service.runAsyncExport(31);

        Optional<CompletedExport> export = service.currentServableExport();
        assertThat(export).isPresent();
        assertThat(zipEntryNames(export.get().content()))
                .containsExactly("1700000000.000001.txt", "1700000000.000001-2.txt", "1700000000.000002.txt");
        assertThat(zipEntryContents(export.get().content()))
                .containsExactly("First content", "Duplicate content", "Second content");
    }

    @Test
    void runAsyncExport_onFailure_setsErrorStatus_andLeavesNoServableExport() {
        SlackChannelRegistry brokenRegistry = mock(SlackChannelRegistry.class);
        when(brokenRegistry.monitoredChannelIds()).thenThrow(new RuntimeException("registry unavailable"));
        SummaryExportService badService =
                new SummaryExportService(asyncJobRepository, threadService, brokenRegistry, applicationContext);

        badService.runAsyncExport(31);

        assertThat(badService.getStatus().running()).isFalse();
        assertThat(badService.getStatus().error()).isNotNull();
        assertThat(badService.currentServableExport()).isEmpty();
        verify(asyncJobRepository).deleteJob("summary-export");
        verifyNoInteractions(threadService);
    }
}
