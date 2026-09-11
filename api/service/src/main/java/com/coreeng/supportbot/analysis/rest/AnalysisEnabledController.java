package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.config.LlmProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always-registered controller for the analysis enabled check.
 * Separate from {@link AnalysisController}, which only exists when an LLM provider is selected
 * ({@code llm.provider}), because the UI needs this endpoint to return {@code {enabled: false}}
 * when analysis is off rather than getting a 404.
 */
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisEnabledController {

    private final LlmProps llmProps;

    @GetMapping("/enabled")
    public ResponseEntity<FeatureStatus> getAnalysisEnabled() {
        return ResponseEntity.ok(new FeatureStatus(llmProps.enabled()));
    }

    public record FeatureStatus(boolean enabled) {}
}
