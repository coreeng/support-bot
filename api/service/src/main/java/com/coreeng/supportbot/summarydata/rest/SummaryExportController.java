package com.coreeng.supportbot.summarydata.rest;

import com.coreeng.supportbot.summarydata.SummaryExportService;
import com.coreeng.supportbot.summarydata.SummaryExportService.CompletedExport;
import com.coreeng.supportbot.summarydata.SummaryExportService.ExportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the async Knowledge Gap thread export: start a job, poll its status, and
 * download the finished zip once ready. See {@link SummaryExportService} for the orchestration.
 */
@Slf4j
@RestController
@RequestMapping("/summary-data/export")
@RequiredArgsConstructor
public class SummaryExportController {

    private final SummaryExportService exportService;

    /**
     * Starts a new export for the given number of days, returning immediately.
     *
     * @param days Number of days to look back (default: 31)
     */
    @PostMapping("/start")
    public ResponseEntity<Void> start(@RequestParam(defaultValue = "31") int days) {
        if (days < 1 || days > 365) {
            return ResponseEntity.badRequest().build();
        }

        if (exportService.start(days)) {
            log.info("Export started for {} days", days);
            return ResponseEntity.accepted().build();
        } else {
            log.warn("Export already running");
            return ResponseEntity.status(409).build(); // Conflict
        }
    }

    @GetMapping("/status")
    public ResponseEntity<SummaryExportStatusResponse> status() {
        ExportStatus status = exportService.getStatus();
        Optional<CompletedExport> export = exportService.currentServableExport();

        return ResponseEntity.ok(new SummaryExportStatusResponse(
                status.running(),
                status.error(),
                export.isPresent(),
                export.map(CompletedExport::displayFilename).orElse(null),
                export.map(CompletedExport::threadCount).orElse(null),
                export.map(CompletedExport::completedAt).orElse(null)));
    }

    /**
     * Streams the currently servable export, if any — never buffers it into a {@code byte[]}.
     */
    @GetMapping(value = "/download", produces = "application/zip")
    public ResponseEntity<Resource> download() {
        Optional<CompletedExport> export = exportService.currentServableExport();
        if (export.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CompletedExport completed = export.get();
        try {
            long size = Files.size(completed.filePath());
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + completed.displayFilename() + "\"")
                    .contentLength(size)
                    .body(new FileSystemResource(completed.filePath()));
        } catch (IOException e) {
            // Not a server fault: the file existed a moment ago in currentServableExport(), so this
            // is a concurrent new export discarding it between that check and this read. Same
            // outcome as "nothing ready" — not found, not an internal error.
            log.warn(
                    "Export file {} disappeared before it could be read (likely a concurrent new export)",
                    completed.filePath(),
                    e);
            return ResponseEntity.notFound().build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryExportStatusResponse(
            boolean running,
            @Nullable String error,
            boolean ready,
            @Nullable String filename,
            @Nullable Integer threadCount,
            @Nullable Instant completedAt) {}
}
