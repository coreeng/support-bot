package com.coreeng.supportbot.docanalysis.rest;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always registered, unlike {@link DocAnalysisController}, so that with the feature off the sidebar
 * gets {@code {"enabled": false}} rather than a 404. The feature's beans exist exactly when an LLM
 * provider is selected and {@code documentation-analysis.enabled} is true.
 */
@RestController
@RequestMapping("/documentation-analysis")
@RequiredArgsConstructor
public class DocAnalysisEnabledController {

    private final LlmProps llmProps;
    private final DocumentationAnalysisProps props;

    @GetMapping("/enabled")
    public ResponseEntity<DocAnalysisStatusUI> getDocumentationAnalysisEnabled() {
        return ResponseEntity.ok(new DocAnalysisStatusUI(llmProps.enabled() && props.enabled()));
    }
}
