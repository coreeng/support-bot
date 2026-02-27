package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/run")
    public ResponseEntity<Void> runAnalysis(@RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > 365) {
            return ResponseEntity.badRequest().build();
        }

        if (analysisService.start(days)) {
            log.info("Analysis started for {} days", days);
            return ResponseEntity.accepted().build();
        } else {
            log.warn("Analysis already running");
            return ResponseEntity.status(409).build(); // Conflict
        }
    }

    @GetMapping("/status")
    public ResponseEntity<AnalysisStatusResponse> getStatus() {
        AnalysisService.AnalysisStatus status = analysisService.getStatus();

        return ResponseEntity.ok(new AnalysisStatusResponse(
                status.jobId(), status.exportedCount(), status.analyzedCount(), status.running(), status.error()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnalysisStatusResponse(
            @Nullable String jobId,
            @Nullable Integer exportedCount,
            @Nullable Integer analyzedCount,
            boolean running,
            @Nullable String error) {}
}
