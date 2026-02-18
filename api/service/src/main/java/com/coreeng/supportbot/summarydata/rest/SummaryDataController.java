package com.coreeng.supportbot.summarydata.rest;

import com.coreeng.supportbot.analysis.AnalysisRepository;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for summary data export and import operations.
 */
@Slf4j
@RestController
@RequestMapping("/summary-data")
@RequiredArgsConstructor
public class SummaryDataController {

    private final ThreadService threadService;
    private final SlackTicketsProps slackTicketsProps;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    /**
     * Export summary data as a zip file containing thread texts.
     * Each file in the zip is named with the thread timestamp.
     *
     * @param days Number of days to look back (default: 31)
     * @return Zip file containing thread texts
     */
    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportSummaryData(@RequestParam(defaultValue = "31") int days) {

        log.info("Exporting summary data for last {} days from channel {}", days, slackTicketsProps.channelId());

        // Fetch all threads with white_check_mark from the configured channel
        ImmutableList<ThreadService.ThreadData> threads =
                threadService.getThreadsWithCheckMarkAsText(slackTicketsProps.channelId(), days);

        log.info("Found {} threads to export", threads.size());

        // Create zip file in memory
        try (var byteArrayOutputStream = new java.io.ByteArrayOutputStream();
                var zip = new java.util.zip.ZipOutputStream(byteArrayOutputStream)) {

            // Add each thread as a separate file in the zip
            for (ThreadService.ThreadData thread : threads) {
                String fileName = thread.threadTs() + ".txt";
                log.debug("Adding thread to zip: {}", fileName);

                zip.putNextEntry(new java.util.zip.ZipEntry(fileName));
                zip.write(thread.text().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }

            zip.finish();
            log.info("Successfully exported {} threads to zip file", threads.size());

            byte[] zipBytes = byteArrayOutputStream.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"content.zip\"")
                    .body(zipBytes);

        } catch (Exception e) {
            log.error("Failed to create zip file", e);
            throw new RuntimeException("Failed to create zip file", e);
        }
    }

    /**
     * Export analysis bundle analysis.zip containing AI prompt and script to run analysis on thread texts.
     * analysis.zip is expected to be in the current directory of the process.
     *
     * @return Analysis bundle zip file
     */
    @GetMapping(value = "/analysis", produces = "application/zip")
    public ResponseEntity<Resource> download() throws IOException {
        Path path = Paths.get("analysis.zip");
        Resource res = new UrlResource(path.toUri());

        if (!res.exists() || !res.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        log.info("Downloading analysis bundle");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis.zip\"")
                .contentLength(Files.size(path))
                .body(res);
    }

    /**
     * Import analysis data from a JSONL file.
     * The file should have the structure: ticket_id, Driver, Category, Feature, Summary
     *
     * @param file JSONL file to import
     * @return Response with the number of records imported
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importAnalysisData(@RequestParam("file") MultipartFile file) {
        log.info("Received import request for file: {}", file.getOriginalFilename());

        try {
            // Parse JSONL file
            List<AnalysisRepository.AnalysisRecord> records = parseJsonlFile(file);
            log.info("Parsed {} records from JSONL file", records.size());

            // Import data using AnalysisService
            int importedCount = analysisService.importAnalysisData(records);

            log.info("Successfully imported {} analysis records", importedCount);
            return ResponseEntity.ok(new ImportResponse(importedCount, "Import successful"));

        } catch (Exception e) {
            log.error("Failed to import analysis data", e);
            return ResponseEntity.badRequest().body(new ImportResponse(0, "Import failed: " + e.getMessage()));
        }
    }

    /**
     * Parse JSONL file into AnalysisRecord objects.
     *
     * @param file JSONL file to parse
     * @return List of AnalysisRecord objects
     * @throws Exception if parsing fails
     */
    private List<AnalysisRepository.AnalysisRecord> parseJsonlFile(MultipartFile file) throws Exception {
        List<AnalysisRepository.AnalysisRecord> records = new ArrayList<>();

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    AnalysisRepository.AnalysisRecord record =
                            objectMapper.readValue(line, AnalysisRepository.AnalysisRecord.class);
                    records.add(record);
                } catch (NumberFormatException e) {
                    log.warn("Skipping line {} - invalid ticket_id", line);
                }
            }
        }

        return records;
    }

    /**
     * Response for import operation.
     */
    public record ImportResponse(int recordsImported, String message) {}
}
