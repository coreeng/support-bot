package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.knowledgegaps.rest.KnowledgeGapsStatusUI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always-registered controller for the analysis enabled check.
 * Separate from {@link AnalysisController} which is conditional on {@code analysis.prompt.enabled},
 * because the UI needs this endpoint to return {@code {enabled: false}} when analysis is off
 * rather than getting a 404.
 */
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisEnabledController {

    private final AnalysisProps analysisProps;

    @GetMapping("/enabled")
    public ResponseEntity<KnowledgeGapsStatusUI> getAnalysisEnabled() {
        return ResponseEntity.ok(
                new KnowledgeGapsStatusUI(analysisProps.prompt().enabled()));
    }
}
