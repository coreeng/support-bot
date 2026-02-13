package com.coreeng.supportbot.summarydata.rest;

import com.coreeng.supportbot.analysis.AnalysisRepository;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    /**
     * Export summary data as a zip file containing thread texts.
     * Each file in the zip is named with the thread timestamp.
     *
     * @param days Number of days to look back (default: 31)
     * @return Zip file containing thread texts
     */
    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> exportSummaryData(@RequestParam(defaultValue = "31") int days) {

        log.info("Exporting summary data for last {} days from channel {}", days, slackTicketsProps.channelId());

        StreamingResponseBody body = out -> {
            try (var zip = new java.util.zip.ZipOutputStream(out)) {
                // Fetch all threads with white_check_mark from the configured channel
                ImmutableList<ThreadService.ThreadData> threads =
                        threadService.getThreadsWithCheckMarkAsText(slackTicketsProps.channelId(), days);

                log.info("Found {} threads to export", threads.size());

                // Add each thread as a separate file in the zip
                for (ThreadService.ThreadData thread : threads) {
                    String fileName = thread.threadTs() + ".txt";
                    log.debug("Adding thread to zip: {}", fileName);

                    zip.putNextEntry(new java.util.zip.ZipEntry(fileName));

                    // Write thread text to the zip entry
                    try (var in = new ByteArrayInputStream(thread.text().getBytes(StandardCharsets.UTF_8))) {
                        in.transferTo(zip);
                    }

                    zip.closeEntry();
                    zip.flush();
                }

                zip.finish();
                log.info("Successfully exported {} threads to zip file", threads.size());
            }
        };

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"content.zip\"")
                .body(body);
    }

    /**
     * Import analysis data from a TSV file.
     * The file should have the structure: ticket_id, Driver, Category, Feature, Summary
     *
     * @param file TSV file to import
     * @return Response with the number of records imported
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importAnalysisData(@RequestParam("file") MultipartFile file) {
        log.info("Received import request for file: {}", file.getOriginalFilename());

        try {
            // Parse TSV file
            List<AnalysisRepository.AnalysisRecord> records = parseTsvFile(file);
            log.info("Parsed {} records from TSV file", records.size());

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
     * Parse TSV file into AnalysisRecord objects.
     *
     * @param file TSV file to parse
     * @return List of AnalysisRecord objects
     * @throws Exception if parsing fails
     */
    private List<AnalysisRepository.AnalysisRecord> parseTsvFile(MultipartFile file) throws Exception {
        List<AnalysisRepository.AnalysisRecord> records = new ArrayList<>();

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("File is empty");
            }

            log.debug("TSV header: {}", headerLine);

            // Read data lines
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\t", -1); // -1 to keep trailing empty strings

                if (fields.length < 5) {
                    log.warn("Skipping line {} - insufficient fields (expected 5, got {})", lineNumber, fields.length);
                    continue;
                }

                try {
                    int ticketId = Integer.parseInt(fields[0].trim());
                    String driver = fields[1].trim();
                    String category = fields[2].trim();
                    String feature = fields[3].trim();
                    String summary = fields[4].trim();

                    records.add(new AnalysisRepository.AnalysisRecord(ticketId, driver, category, feature, summary));

                } catch (NumberFormatException e) {
                    log.warn("Skipping line {} - invalid ticket_id: {}", lineNumber, fields[0]);
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
