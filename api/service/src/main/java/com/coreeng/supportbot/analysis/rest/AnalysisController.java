package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.knowledgegaps.rest.KnowledgeGapsStatusUI;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;

    private final AnalysisProps analysisProps;

    @GetMapping("/enabled")
    public ResponseEntity<KnowledgeGapsStatusUI> getKnowledgeGapsStatus() {
        return ResponseEntity.ok(
                new KnowledgeGapsStatusUI(analysisProps.prompt().enabled()));
    }

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
