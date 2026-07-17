package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.prtracking.*;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant-insights")
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantInsightsController {

    private final TenantInsightsService tenantInsightsService;

    @GetMapping("/pr-stats")
    public List<RepoInsights> prStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        return tenantInsightsService.prStats(dateFrom, dateTo);
    }

    @GetMapping("/request-breakdown")
    public RequestBreakdown requestBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        return tenantInsightsService.requestBreakdown(dateFrom, dateTo);
    }

    @GetMapping("/in-flight-prs")
    public List<InFlightPrResponse> inFlightPrs(@RequestParam(required = false) @Nullable String team) {
        return tenantInsightsService.inFlightPrs(team);
    }
}
