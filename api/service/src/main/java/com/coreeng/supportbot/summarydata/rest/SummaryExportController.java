package com.coreeng.supportbot.summarydata.rest;

import com.coreeng.supportbot.summarydata.SummaryExportService;
import com.coreeng.supportbot.summarydata.SummaryExportService.CompletedExport;
import com.coreeng.supportbot.summarydata.SummaryExportService.ExportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<SummaryExportStatusResponse> status() {
        ExportStatus status = exportService.getStatus();
        Optional<CompletedExport> export = exportService.currentServableExport();

        return ResponseEntity.ok(new SummaryExportStatusResponse(
                status.running(),
                status.startedAt(),
                status.error(),
                export.isPresent(),
                export.map(CompletedExport::displayFilename).orElse(null),
                export.map(CompletedExport::threadCount).orElse(null),
                export.map(CompletedExport::completedAt).orElse(null)));
    }

    // Single-consumption (see SummaryExportService#consumeCurrentExport): a second request after
    // the first gets 404, not a bug.
    @GetMapping(value = "/download", produces = "application/zip")
    public ResponseEntity<Resource> download() {
        Optional<CompletedExport> export = exportService.consumeCurrentExport();
        if (export.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CompletedExport completed = export.get();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + completed.displayFilename() + "\"")
                .contentLength(completed.content().length)
                .body(new ByteArrayResource(completed.content()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryExportStatusResponse(
            boolean running,
            @Nullable Instant startedAt,
            @Nullable String error,
            boolean ready,
            @Nullable String filename,
            @Nullable Integer threadCount,
            @Nullable Instant completedAt) {}
}
