package com.coreeng.supportbot.summarydata.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.summarydata.SummaryExportService;
import com.coreeng.supportbot.summarydata.SummaryExportService.CompletedExport;
import com.coreeng.supportbot.summarydata.SummaryExportService.ExportStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SummaryExportControllerTest {

    @Mock
    private SummaryExportService exportService;

    private SummaryExportController controller;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        controller = new SummaryExportController(exportService);
    }

    // --- start ---

    @Test
    void start_returns202_whenStartSucceeds() {
        when(exportService.start(7)).thenReturn(true);

        ResponseEntity<Void> response = controller.start(7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void start_returns409_whenAlreadyRunning() {
        when(exportService.start(7)).thenReturn(false);

        ResponseEntity<Void> response = controller.start(7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void start_returns400_whenDaysInvalid() {
        assertThat(controller.start(0).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.start(366).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.start(-1).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoMoreInteractions(exportService);
    }

    // --- status ---

    @Test
    void status_reportsNotReady_whenNoExportAndIdle() {
        when(exportService.getStatus()).thenReturn(new ExportStatus(false, null, null));
        when(exportService.currentServableExport()).thenReturn(Optional.empty());

        ResponseEntity<SummaryExportController.SummaryExportStatusResponse> response = controller.status();

        SummaryExportController.SummaryExportStatusResponse body = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(body.running()).isFalse();
        assertThat(body.startedAt()).isNull();
        assertThat(body.error()).isNull();
        assertThat(body.ready()).isFalse();
        assertThat(body.filename()).isNull();
        assertThat(body.threadCount()).isNull();
        assertThat(body.completedAt()).isNull();
    }

    @Test
    void status_reportsRunning_whileJobInProgress() {
        Instant startedAt = Instant.now();
        when(exportService.getStatus()).thenReturn(new ExportStatus(true, startedAt, null));
        when(exportService.currentServableExport()).thenReturn(Optional.empty());

        ResponseEntity<SummaryExportController.SummaryExportStatusResponse> response = controller.status();

        SummaryExportController.SummaryExportStatusResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.running()).isTrue();
        assertThat(body.startedAt()).isEqualTo(startedAt);
        assertThat(body.ready()).isFalse();
    }

    @Test
    void status_reportsReady_withExportDetails_whenCompleted() {
        Instant completedAt = Instant.now();
        CompletedExport export = new CompletedExport(
                Path.of("/tmp/summary-export/downloaded-threads-2026-06-03-to-2026-07-03.zip"),
                "downloaded-threads-2026-06-03-to-2026-07-03.zip",
                12,
                completedAt);
        when(exportService.getStatus()).thenReturn(new ExportStatus(false, completedAt, null));
        when(exportService.currentServableExport()).thenReturn(Optional.of(export));

        ResponseEntity<SummaryExportController.SummaryExportStatusResponse> response = controller.status();

        SummaryExportController.SummaryExportStatusResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.running()).isFalse();
        assertThat(body.ready()).isTrue();
        assertThat(body.filename()).isEqualTo("downloaded-threads-2026-06-03-to-2026-07-03.zip");
        assertThat(body.threadCount()).isEqualTo(12);
        assertThat(body.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void status_reportsError_whenLastRunFailed() {
        when(exportService.getStatus()).thenReturn(new ExportStatus(false, Instant.now(), "Slack API error"));
        when(exportService.currentServableExport()).thenReturn(Optional.empty());

        ResponseEntity<SummaryExportController.SummaryExportStatusResponse> response = controller.status();

        SummaryExportController.SummaryExportStatusResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("Slack API error");
        assertThat(body.ready()).isFalse();
    }

    // --- download ---

    @Test
    void download_returns404_whenNoExportReady() {
        when(exportService.currentServableExport()).thenReturn(Optional.empty());

        ResponseEntity<Resource> response = controller.download();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void download_returns200_withCorrectHeadersAndStreamedBody_whenReady() throws IOException {
        Path file = tempDir.resolve("downloaded-threads-2026-06-03-to-2026-07-03.zip");
        Files.writeString(file, "zip bytes go here");
        CompletedExport export =
                new CompletedExport(file, "downloaded-threads-2026-06-03-to-2026-07-03.zip", 3, Instant.now());
        when(exportService.currentServableExport()).thenReturn(Optional.of(export));

        ResponseEntity<Resource> response = controller.download();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=\"downloaded-threads-2026-06-03-to-2026-07-03.zip\"");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(Files.size(file));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContentAsByteArray()).isEqualTo(Files.readAllBytes(file));
    }

    @Test
    void download_returns404_whenFileDisappearsBetweenCheckAndRead() {
        // currentServableExport() reports an export, but the file itself is gone by the time we try
        // to read it — the same race the controller's javadoc/comment calls out explicitly.
        Path missingFile = tempDir.resolve("already-deleted.zip");
        CompletedExport export = new CompletedExport(missingFile, "already-deleted.zip", 3, Instant.now());
        when(exportService.currentServableExport()).thenReturn(Optional.of(export));

        ResponseEntity<Resource> response = controller.download();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
