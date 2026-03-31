package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.config.PrTrackingProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant-insights")
@RequiredArgsConstructor
public class TenantInsightsEnabledController {

    private final PrTrackingProps prTrackingProps;

    @GetMapping("/enabled")
    public ResponseEntity<FeatureStatus> enabled() {
        return ResponseEntity.ok(new FeatureStatus(prTrackingProps.enabled()));
    }

    public record FeatureStatus(boolean enabled) {}
}
