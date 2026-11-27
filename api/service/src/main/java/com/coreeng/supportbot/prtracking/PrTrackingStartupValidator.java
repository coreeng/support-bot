package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TagsRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validates that the tag and impact codes configured in pr-review-tracking
 * exist in the registry. Runs after {@code RegistryInitialisation} (@Order(100))
 * so the registry is fully seeded before this check executes. Also rejects outcome
 * emoji settings that would clash with other reactions on the same query message.
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
    private final SlackTicketsProps slackTicketsProps;

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
        validateOutcomeEmojiCollisions();
        log.atInfo().log("PR tracking config validation passed");
    }

    private void validateOutcomeEmojiCollisions() {
        PrTrackingProps.OutcomeEmojis emojis = props.outcomeEmojis();
        if (emojis.merged() == null && emojis.closed() == null) {
            return;
        }
        Set<String> ticketReactions = new LinkedHashSet<>(List.of(
                slackTicketsProps.expectedInitialReaction(),
                slackTicketsProps.responseInitialReaction(),
                slackTicketsProps.resolvedReaction(),
                slackTicketsProps.escalatedReaction()));
        if (ticketReactions.contains(props.prEmoji())) {
            throw new IllegalStateException(
                    ("pr-review-tracking.pr-emoji '%s' is also a ticket reaction (one of %s); with outcome-emojis set"
                                    + " the bot removes pr-emoji, which would remove that ticket reaction too")
                            .formatted(props.prEmoji(), ticketReactions));
        }
        Set<String> taken = new LinkedHashSet<>(ticketReactions);
        taken.add(props.prEmoji());
        checkNoCollision("merged", emojis.merged(), taken);
        checkNoCollision("closed", emojis.closed(), taken);
    }

    private static void checkNoCollision(String field, @Nullable String emoji, Set<String> taken) {
        if (emoji != null && taken.contains(emoji)) {
            throw new IllegalStateException(
                    "pr-review-tracking.outcome-emojis.%s '%s' is already used by another reaction on the query message (one of %s, which includes pr-emoji); choose a different emoji"
                            .formatted(field, emoji, taken));
        }
    }
}
