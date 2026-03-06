package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TagsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validates that the tag and impact codes configured in pr-review-tracking
 * exist in the registry. Runs after {@code RegistryInitialisation} (@Order(100))
 * so the registry is fully seeded before this check executes.
 */
@Component
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@Order(101)
@RequiredArgsConstructor
@Slf4j
public class PrTrackingStartupValidator implements ApplicationRunner {

    private final PrTrackingProps props;
    private final TagsRegistry tagsRegistry;
    private final ImpactsRegistry impactsRegistry;

    @Override
    public void run(ApplicationArguments args) {
        log.atInfo().log("Validating PR tracking config against registry");
        for (String tagCode : props.tags()) {
            if (tagsRegistry
                    .listTagsByCodes(com.google.common.collect.ImmutableList.of(tagCode))
                    .isEmpty()) {
                throw new IllegalStateException(
                        "pr-review-tracking: unknown tag code '%s'. Check enums.tags config.".formatted(tagCode));
            }
        }
        if (impactsRegistry.findImpactByCode(props.impact()) == null) {
            throw new IllegalStateException("pr-review-tracking: unknown impact code '%s'. Check enums.impacts config."
                    .formatted(props.impact()));
        }
        log.atInfo().log("PR tracking config validation passed");
    }
}
