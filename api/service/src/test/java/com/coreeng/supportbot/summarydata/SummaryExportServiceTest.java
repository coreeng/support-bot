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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
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

    @TempDir
    Path tempDir;

    private SlackChannelRegistry channelRegistry;
    private SummaryExportService service;

    @BeforeEach
    void setUp() {
        channelRegistry = new SlackChannelRegistry(
                new SlackTicketsProps("C123", List.of(), "eyes", "eyes", "white_check_mark", "sos"));
        service = new SummaryExportService(asyncJobRepository, threadService, channelRegistry, applicationContext);
        service.exportDirectory = tempDir;
    }

    private Path createTempFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "content");
        return file;
    }

    private static List<String> zipEntryNames(Path zipFile) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    private static List<String> zipEntryContents(Path zipFile) throws IOException {
        List<String> contents = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
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

    // --- start()/status/eviction (unchanged behavior, still covered) ---

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
    void start_discardsPreviousCompletedExport_andDeletesOldFileImmediately() throws IOException {
        Path oldFile = createTempFile("old-export.zip");
        service.currentExport.set(new CompletedExport(oldFile, "old-export.zip", 3, Instant.now()));

        when(asyncJobRepository.tryStartJob("summary-export", "7")).thenReturn(true);
        when(applicationContext.getBean(SummaryExportService.class)).thenReturn(service);
        when(threadService.getThreadsWithCheckMarkAsText(anyString(), anyInt())).thenReturn(ImmutableList.of());

        service.start(7);

        assertThat(Files.exists(oldFile)).isFalse();
    }

    @Test
    void getStatus_shouldReturnIdleByDefault() {
        ExportStatus status = service.getStatus();

        assertThat(status.running()).isFalse();
        assertThat(status.startedAt()).isNull();
        assertThat(status.error()).isNull();
    }

    @Test
    void currentServableExport_returnsEmptyWhenNoneCompleted() {
        assertThat(service.currentServableExport()).isEmpty();
    }

    @Test
    void currentServableExport_returnsCurrentExport_regardlessOfAge() throws IOException {
        Path file = createTempFile("export.zip");
        CompletedExport export =
                new CompletedExport(file, "export.zip", 5, Instant.now().minus(10, ChronoUnit.DAYS));
        service.currentExport.set(export);

        Optional<CompletedExport> result = service.currentServableExport();

        assertThat(result).contains(export);
        assertThat(Files.exists(file)).isTrue();
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
        assertThat(export.get().filePath()).exists();

        assertThat(zipEntryNames(export.get().filePath()))
                .containsExactly("1700000000.000001.txt", "1700000000.000002.txt");
        assertThat(zipEntryContents(export.get().filePath()))
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
        multiService.exportDirectory = tempDir;

        when(threadService.getThreadsWithCheckMarkAsText("C-a", 31)).thenThrow(new RuntimeException("not_in_channel"));
        when(threadService.getThreadsWithCheckMarkAsText("C-b", 31))
                .thenReturn(ImmutableList.of(new ThreadService.ThreadData("1700000000.000009", "Channel B content")));

        multiService.runAsyncExport(31);

        assertThat(multiService.getStatus().error()).isNull();
        Optional<CompletedExport> export = multiService.currentServableExport();
        assertThat(export).isPresent();
        assertThat(zipEntryNames(export.get().filePath())).containsExactly("1700000000.000009.txt");
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
        multiService.exportDirectory = tempDir;

        when(threadService.getThreadsWithCheckMarkAsText("C-a", 31))
                .thenReturn(ImmutableList.of(new ThreadService.ThreadData("1700000000.000001", "from A")));
        when(threadService.getThreadsWithCheckMarkAsText("C-b", 31))
                .thenReturn(ImmutableList.of(new ThreadService.ThreadData("1700000000.000001", "from B")));

        multiService.runAsyncExport(31);

        Optional<CompletedExport> export = multiService.currentServableExport();
        assertThat(export).isPresent();
        assertThat(zipEntryNames(export.get().filePath()))
                .containsExactlyInAnyOrder("1700000000.000001.txt", "1700000000.000001-2.txt");
        assertThat(zipEntryContents(export.get().filePath())).containsExactlyInAnyOrder("from A", "from B");
    }

    @Test
    void runAsyncExport_writesEmptyZip_whenNoThreadsFound() throws IOException {
        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(ImmutableList.of());

        service.runAsyncExport(31);

        Optional<CompletedExport> export = service.currentServableExport();
        assertThat(export).isPresent();
        assertThat(export.get().threadCount()).isEqualTo(0);
        assertThat(zipEntryNames(export.get().filePath())).isEmpty();
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
        assertThat(zipEntryNames(export.get().filePath()))
                .containsExactly("1700000000.000001.txt", "1700000000.000001-2.txt", "1700000000.000002.txt");
        assertThat(zipEntryContents(export.get().filePath()))
                .containsExactly("First content", "Duplicate content", "Second content");
    }

    @Test
    void runAsyncExport_onFailure_setsErrorStatus_andLeavesNoServableExport() throws IOException {
        // exportDirectory points at a path that already exists as a plain file, so
        // Files.createDirectories(...) fails and the run errors out before writing anything
        Path blocked = tempDir.resolve("blocked-file");
        Files.writeString(blocked, "not a directory");
        SummaryExportService badService =
                new SummaryExportService(asyncJobRepository, threadService, channelRegistry, applicationContext);
        badService.exportDirectory = blocked;

        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(ImmutableList.of());

        badService.runAsyncExport(31);

        assertThat(badService.getStatus().running()).isFalse();
        assertThat(badService.getStatus().error()).isNotNull();
        assertThat(badService.currentServableExport()).isEmpty();
        verify(asyncJobRepository).deleteJob("summary-export");
    }
}
