package com.coreeng.supportbot.elevate;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes whether the Elevate integration is configured. Deliberately unrestricted by role (unlike
 * {@link ElevateStatusController}'s {@code @PreAuthorize}) — still requires authentication — so the
 * UI sidebar can check this for every user to decide whether to show the Elevate nav item, without a
 * 403 for users lacking the restricted-dashboards role.
 */
@RestController
@RequestMapping("/elevate")
@RequiredArgsConstructor
public class ElevateEnabledController {

    private final ElevateQueryService elevateQueryService;

    @GetMapping("/enabled")
    public FeatureStatus enabled() {
        return new FeatureStatus(elevateQueryService.configured());
    }

    public record FeatureStatus(boolean enabled) {}
}
