package com.coreeng.supportbot.summary.rest;

import com.coreeng.supportbot.config.SummaryProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always-registered feature check for the Support Summary page, mirroring
 * {@link com.coreeng.supportbot.analysis.rest.AnalysisEnabledController}.
 *
 * <p>Deliberately not {@code @ConditionalOnProperty} — unlike {@link SummaryController}, which only
 * exists when the feature is on. The sidebar asks this to decide whether to show the nav item, and
 * with the feature off it needs {@code {"enabled": false}} rather than a 404 it would have to treat
 * as an error.
 *
 * <p>{@code summary.enabled} alone is the whole answer: it cannot be true without
 * {@code analysis.prompt.enabled}, which {@link com.coreeng.supportbot.config.SummaryValidationConfig}
 * enforces at startup.
 */
@RestController
@RequestMapping("/summary")
@RequiredArgsConstructor
public class SummaryEnabledController {

    private final SummaryProps summaryProps;

    @GetMapping("/enabled")
    public ResponseEntity<SummaryStatusUI> getSummaryEnabled() {
        return ResponseEntity.ok(new SummaryStatusUI(summaryProps.enabled()));
    }
}
