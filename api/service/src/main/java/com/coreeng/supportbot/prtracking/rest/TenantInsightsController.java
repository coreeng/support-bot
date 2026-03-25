package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import com.coreeng.supportbot.prtracking.RepoInsights;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant-insights")
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantInsightsController {

    private static final Set<String> VALID_WINDOWS = Set.of("7d", "30d", "90d");

    private final PrTrackingRepository prTrackingRepository;

    @GetMapping("/enabled")
    public FeatureStatus enabled() {
        return new FeatureStatus(true);
    }

    public record FeatureStatus(boolean enabled) {}

    @GetMapping("/pr-stats")
    public ResponseEntity<List<RepoInsights>> stats(@RequestParam(defaultValue = "30d") String window) {
        if (!VALID_WINDOWS.contains(window)) {
            return ResponseEntity.badRequest().build();
        }
        int windowDays = Integer.parseInt(window.replace("d", ""));
        return ResponseEntity.ok(prTrackingRepository.getInsightsByRepo(windowDays));
    }
}
