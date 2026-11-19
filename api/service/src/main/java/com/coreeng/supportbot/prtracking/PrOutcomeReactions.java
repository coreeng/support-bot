package com.coreeng.supportbot.prtracking;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Swaps the ticket's {@code pr-emoji} reaction for a configured outcome emoji ({@code
 * pr-review-tracking.outcome-emojis}) once every tracked PR on the ticket has resolved, so the query
 * message normally carries one PR marker reflecting where things ended up. If a removal fails after the
 * add succeeded, both emojis remain; that is logged, not retried.
 *
 * <p>Additive: with no outcome emoji configured, nothing here touches the database or Slack and {@code
 * pr-emoji} stays as before. Best-effort: once configured, the public methods catch and log every
 * runtime failure (Slack, repository, a ticket without an id) rather than throwing, so the emoji can't
 * break PR detection or the poller's closure flow.
 */
@Component
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PrOutcomeReactions {

    private final PrTrackingProps prTrackingProps;
    private final PrTrackingRepository prTrackingRepository;
    private final TicketSlackService ticketSlackService;

    private static final Set<String> CONFIG_SLACK_ERRORS =
            Set.of("invalid_name", "missing_scope", "not_in_channel", "channel_not_found", "no_permission");

    /**
     * Called once a PR on {@code ticket} has been written {@code CLOSED} for {@code event} ({@link
     * MessageEvent#APPROVED}, {@link MessageEvent#MERGED} or {@link MessageEvent#CLOSED}). When no tracked PR
     * on the ticket is still active, adds the outcome emoji, then removes {@code pr-emoji} and the other
     * outcome emoji. A failed add throws before the removals, so it leaves the open marker rather than no
     * marker.
     *
     * <p>Outcome: a ticket with a single tracked PR gets {@code closed} when it was closed without merging
     * and {@code merged} otherwise (merged, or approved and ready to merge). A ticket with several tracked PRs
     * always gets {@code merged} once they are all done, even if some or all were closed without merging —
     * per-PR outcomes aren't stored.
     */
    public void onPrResolved(Ticket ticket, MessageEvent event) {
        PrTrackingProps.OutcomeEmojis emojis = prTrackingProps.outcomeEmojis();
        String merged = emojis.merged();
        String closed = emojis.closed();
        if (merged == null && closed == null) {
            return;
        }
        String step = "look up the ticket's tracked PRs";
        try {
            long ticketId = checkNotNull(ticket.id()).id();
            if (prTrackingRepository.hasAnyActiveForTicket(ticketId)) {
                log.atDebug()
                        .addArgument(ticketId)
                        .log("Ticket {} still has active tracked PRs, keeping the open PR emoji");
                return;
            }
            boolean onlyPrClosedWithoutMerge =
                    event == MessageEvent.CLOSED && prTrackingRepository.countForTicket(ticketId) <= 1;
            String outcomeEmoji = onlyPrClosedWithoutMerge ? closed : merged;
            if (outcomeEmoji == null) {
                return;
            }
            MessageRef queryRef = ticket.queryRef();
            step = "add :" + outcomeEmoji + ":";
            ticketSlackService.addReaction(queryRef, outcomeEmoji);
            step = "remove :" + prTrackingProps.prEmoji() + ":";
            ticketSlackService.removeReaction(queryRef, prTrackingProps.prEmoji());
            // The other outcome emoji can linger from an earlier resolution whose clear-on-retrack failed;
            // remove it so the message never shows both.
            String otherOutcomeEmoji = outcomeEmoji.equals(merged) ? closed : merged;
            if (otherOutcomeEmoji != null && !otherOutcomeEmoji.equals(outcomeEmoji)) {
                step = "remove :" + otherOutcomeEmoji + ":";
                ticketSlackService.removeReaction(queryRef, otherOutcomeEmoji);
            }
        } catch (RuntimeException e) {
            logFailure("apply the PR outcome emoji", ticket, step, e);
        }
    }

    /**
     * Called once {@code pr-emoji} is on the message for a newly tracked PR (callers skip it when that add
     * failed): clears any outcome emoji left by earlier PRs that had all resolved, so the ticket shows only
     * the open marker. Only a ticket with an earlier tracked PR can carry an outcome emoji, so a ticket's
     * first PR makes no Slack calls.
     */
    public void onPrTracked(Ticket ticket) {
        PrTrackingProps.OutcomeEmojis emojis = prTrackingProps.outcomeEmojis();
        String merged = emojis.merged();
        String closed = emojis.closed();
        if (merged == null && closed == null) {
            return;
        }
        String step = "look up the ticket's tracked PRs";
        try {
            long ticketId = checkNotNull(ticket.id()).id();
            if (prTrackingRepository.countForTicket(ticketId) <= 1) {
                return;
            }
            MessageRef queryRef = ticket.queryRef();
            if (merged != null) {
                step = "remove :" + merged + ":";
                ticketSlackService.removeReaction(queryRef, merged);
            }
            if (closed != null && !closed.equals(merged)) {
                step = "remove :" + closed + ":";
                ticketSlackService.removeReaction(queryRef, closed);
            }
        } catch (RuntimeException e) {
            logFailure("clear the PR outcome emoji", ticket, step, e);
        }
    }

    /**
     * Slack errors that won't go away on their own (a misspelled emoji name, a missing scope, the bot not in
     * the channel) are logged at ERROR so they stand out from transient failures such as rate limiting.
     */
    private static void logFailure(String action, Ticket ticket, String step, RuntimeException e) {
        String slackError = e instanceof SlackException slackException ? slackException.getError() : null;
        if (slackError != null && CONFIG_SLACK_ERRORS.contains(slackError)) {
            log.atError()
                    .setCause(e)
                    .addArgument(action)
                    .addArgument(ticket::id)
                    .addArgument(step)
                    .addArgument(slackError)
                    .log("Failed to {} for ticket {} ({}): Slack rejected it with {}; check"
                            + " pr-review-tracking.outcome-emojis and the bot's Slack scopes");
            return;
        }
        log.atWarn()
                .setCause(e)
                .addArgument(action)
                .addArgument(ticket::id)
                .addArgument(step)
                .addArgument(
                        () -> slackError != null ? slackError : e.getClass().getSimpleName())
                .log("Failed to {} for ticket {} ({}: {}); its reactions may be partly updated");
    }
}
