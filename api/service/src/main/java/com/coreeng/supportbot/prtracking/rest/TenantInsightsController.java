package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.prtracking.EscalationBreakdown;
import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import com.coreeng.supportbot.prtracking.RepoInsights;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/tenant-insights")
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantInsightsController {

    private static final FeatureStatus ENABLED = new FeatureStatus(true);

    private final PrTrackingRepository prTrackingRepository;

    @GetMapping("/enabled")
    public FeatureStatus enabled() {
        return ENABLED;
    }

    public record FeatureStatus(boolean enabled) {}

    @GetMapping("/pr-stats")
    public List<RepoInsights> prStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must not be after dateTo");
        }
        return prTrackingRepository.getInsightsByRepo(dateFrom, dateTo);
    }

    @GetMapping("/escalation-breakdown")
    public EscalationBreakdown escalationBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must not be after dateTo");
        }
        return prTrackingRepository.getEscalationBreakdown(dateFrom, dateTo);
    }
}
