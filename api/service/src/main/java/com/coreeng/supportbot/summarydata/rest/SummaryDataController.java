package com.coreeng.supportbot.summarydata.rest;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

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

    /**
     * Export summary data as a zip file containing thread texts.
     * Each file in the zip is named with the thread timestamp.
     *
     * @param days Number of days to look back (default: 30)
     * @return Zip file containing thread texts
     */
    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> exportSummaryData(
            @RequestParam(defaultValue = "10") int days) {

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

}

