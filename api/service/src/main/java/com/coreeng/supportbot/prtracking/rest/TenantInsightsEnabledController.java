package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.config.PrTrackingProps;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the PR tracking feature-flag status. Separated from {@link TenantInsightsController} so
 * this endpoint remains registered even when PR tracking is disabled via
 * {@code @ConditionalOnProperty}.
 */
@RestController
@RequestMapping("/tenant-insights")
@RequiredArgsConstructor
public class TenantInsightsEnabledController {

    private final PrTrackingProps prTrackingProps;

    @GetMapping("/enabled")
    public FeatureStatus enabled() {
        return new FeatureStatus(prTrackingProps.enabled());
    }

    public record FeatureStatus(boolean enabled) {}
}
