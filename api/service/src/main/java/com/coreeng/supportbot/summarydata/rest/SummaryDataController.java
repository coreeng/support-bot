package com.coreeng.supportbot.summarydata.rest;

import com.coreeng.supportbot.analysis.AnalysisRepository;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.fasterxml.jackson.core.JacksonException;
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
import java.util.zip.ZipException;
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
     * @param days Number of days to look back (default: 7)
     * @return Zip file containing thread texts
     */
    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportSummaryData(@RequestParam(defaultValue = "7") int days) {

        // Clamp days to 1-365
        days = Math.min(Math.max(days, 1), 365);

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

        } catch (ZipException e) {
            log.error("Zip format error has occurred", e);
            throw new RuntimeException("Zip format error has occurred", e);
        } catch (IOException e) {
            // byte array write error
            log.error("An I/O error has occurred", e);
            throw new RuntimeException("An I/O error has occurred", e);
        } catch (NullPointerException e) {
            // Charset unavailable
            log.error("Configuration error has occurred", e);
            throw new RuntimeException("Configuration error has occurred", e);
        } catch (Exception e) {
            log.error("Zip stream cannot be closed", e);
            throw new RuntimeException("Zip stream cannot be closed", e);
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
    public ResponseEntity<ImportResponse> importAnalysisData(
            @RequestParam(value = "file", required = false) MultipartFile file) {

        // Validate file is provided
        if (file == null) {
            log.warn("Import request received with no file");
            return ResponseEntity.badRequest().body(new ImportResponse(0, "No file provided"));
        } else if (file.isEmpty()) {
            log.warn("Import request received with empty file");
            return ResponseEntity.badRequest().body(new ImportResponse(0, "File is empty"));
        }

        log.info("Received import request for file: {}", file.getOriginalFilename());

        try {
            // Parse JSONL file
            List<AnalysisRepository.AnalysisRecord> records = new ArrayList<>();
            int totalLines = parseJsonlFile(file, records);

            if (records.isEmpty()) {
                log.warn("Import request received with no valid records");
                return ResponseEntity.badRequest().body(new ImportResponse(0, "Not a valid JSONL file"));
            }

            log.info("Parsed {} records from JSONL file of {} lines", records.size(), totalLines);

            if (totalLines == records.size()) {
                // Import data using AnalysisService
                int importedCount = analysisService.importAnalysisData(records);

                log.info("Successfully imported {} analysis records", importedCount);

                return ResponseEntity.ok(new ImportResponse(importedCount, "Import successful"));
            }

            return ResponseEntity.badRequest()
                    .body(new ImportResponse(0, "Import skipped as some records were malformed or incomplete"));

        } catch (Exception e) {
            log.error("Failed to import analysis data", e);
            return ResponseEntity.internalServerError().body(new ImportResponse(0, "Import failed: " + e.getMessage()));
        }
    }

    /**
     * Parse JSONL file into AnalysisRecord objects.
     *
     * @param file JSONL file to parse
     * @param records List to add parsed records to
     * @return boolean some records were dropped
     */
    private int parseJsonlFile(MultipartFile file, List<AnalysisRepository.AnalysisRecord> records) {
        int totalLines = 0;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    ++totalLines;
                    AnalysisRepository.AnalysisRecord record =
                            objectMapper.readValue(line, AnalysisRepository.AnalysisRecord.class);
                    if (!record.isValid()) {
                        log.warn("Skipping line - missing or empty fields: {}", line);
                        continue;
                    }
                    records.add(record);
                } catch (JacksonException e) {
                    log.warn("Skipping line  - invalid JSON: {}", line);
                } catch (NumberFormatException e) {
                    log.warn("Skipping line - invalid ticket_id: {}", line);
                }
            }
        } catch (IOException e) {
            log.error("Unable to read input file");
            throw new RuntimeException("Unable to read input file", e);
        }

        return totalLines;
    }

    /**
     * Response for import operation.
     */
    public record ImportResponse(int recordsImported, String message) {}
}
