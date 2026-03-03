package com.coreeng.supportbot.prtracking;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.coreeng.supportbot.ticket.TicketTeamSuggestionsService;
import com.coreeng.supportbot.ticket.TicketTeamsSuggestion;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PrDetectionService {

    private static final DateTimeFormatter DEADLINE_FMT =
            DateTimeFormatter.ofPattern("EEE dd MMM 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final GitHubPrUrlParser prUrlParser;
    private final GitHubClient gitHubClient;
    private final PrTrackingRepository prTrackingRepository;
    private final PrTrackingProps prTrackingProps;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final EscalationProcessingService escalationProcessingService;
    private final TicketSlackService ticketSlackService;
    private final TicketRepository ticketRepository;
    private final TicketTeamSuggestionsService ticketTeamSuggestionsService;
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;

    public boolean containsPrLinks(String message) {
        return !prUrlParser.parse(message).isEmpty();
    }

    public PrDetectionOutcome handleMessagePosted(MessagePosted event, Ticket ticket) {
        List<DetectedPr> detectedPrs = prUrlParser.parse(event.message());
        if (detectedPrs.isEmpty()) {
            return PrDetectionOutcome.skipped();
        }

        TicketId ticketId = checkNotNull(ticket.id());
        boolean anyOpenTracked = false;
        boolean metadataInitialized = false;
        boolean baseReactionsAdded = false;

        for (DetectedPr pr : detectedPrs) {
            if (prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(
                    ticketId.id(), pr.repositoryName(), pr.pullNumber())) {
                log.atInfo()
                        .addArgument(pr::repositoryName)
                        .addArgument(pr::pullNumber)
                        .addArgument(ticketId::id)
                        .log("PR {}#{} already tracked for ticket {}, skipping");
                continue;
            }
            if (!baseReactionsAdded) {
                addReaction(slackTicketsProps.expectedInitialReaction(), ticket.queryTs(), ticket.channelId());
                ticketSlackService.markPostTracked(ticket.queryRef());
                baseReactionsAdded = true;
            }
            boolean closeTicketOnResolve = !event.messageRef().isReply();
            PerPrResult result = processPr(pr, ticket, closeTicketOnResolve);
            switch (result) {
                case TRACKED -> {
                    anyOpenTracked = true;
                    if (!metadataInitialized && !event.messageRef().isReply()) {
                        ticket = initializePrMetadataIfNeeded(ticket, event);
                        metadataInitialized = true;
                    }
                }
                case SKIPPED -> {}
            }
        }

        if (anyOpenTracked) {
            return PrDetectionOutcome.tracked();
        }
        return PrDetectionOutcome.skipped();
    }

    private enum PerPrResult {
        TRACKED,
        SKIPPED
    }

    private PerPrResult processPr(DetectedPr detectedPr, Ticket ticket, boolean closeTicketOnResolve) {
        PrTrackingProps.Repository repoConfig = prTrackingProps.repositories().stream()
                .filter(r -> r.name().equals(detectedPr.repositoryName()))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Repo config not found for " + detectedPr.repositoryName()));

        GitHubPullRequest prMetadata;
        try {
            prMetadata = gitHubClient.getPullRequest(detectedPr.repositoryName(), detectedPr.pullNumber());
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .addArgument(e::getMessage)
                    .log("Could not fetch PR metadata for {}#{}, skipping: {}");
            return PerPrResult.SKIPPED;
        }

        if (!prMetadata.isOpen()) {
            log.atInfo()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .addArgument(prMetadata::state)
                    .log("PR {}#{} is {} — skipping tracking");
            return PerPrResult.SKIPPED;
        }

        Instant slaDeadline = prMetadata.createdAt().plus(repoConfig.sla());
        String teamLabel = resolveTeamLabel(repoConfig.owningTeam());
        TicketId ticketId = checkNotNull(ticket.id());

        PrTrackingRecord tracking = prTrackingRepository.insertIfAbsent(new NewPrTracking(
                ticketId.id(),
                detectedPr.repositoryName(),
                detectedPr.pullNumber(),
                prMetadata.createdAt(),
                slaDeadline,
                repoConfig.owningTeam(),
                closeTicketOnResolve));
        if (tracking == null) {
            log.atInfo()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .addArgument(ticketId::id)
                    .log("PR {}#{} became tracked concurrently for ticket {}, skipping");
            return PerPrResult.SKIPPED;
        }

        log.atInfo()
                .addArgument(detectedPr::repositoryName)
                .addArgument(detectedPr::pullNumber)
                .addArgument(ticketId::id)
                .log("PR {}#{} tracking record created for ticket {}");

        MessageTs queryTs = ticket.queryTs();
        String channelId = ticket.channelId();
        addReaction(prTrackingProps.prEmoji(), queryTs, channelId);

        if (Instant.now().isAfter(slaDeadline)) {
            postSlaBreachReply(detectedPr, repoConfig.sla(), queryTs, channelId);
            escalateImmediately(tracking, ticket, repoConfig.owningTeam());
        } else {
            postSlaReply(detectedPr, repoConfig.sla(), teamLabel, slaDeadline, queryTs, channelId);
        }
        return PerPrResult.TRACKED;
    }

    private Ticket initializePrMetadataIfNeeded(Ticket ticket, MessagePosted event) {
        TicketTeam resolvedTeam = ticket.team() != null ? ticket.team() : resolveFirstSuggestedTeam(event.userId());
        ImmutableList<String> resolvedTags =
                ticket.tags().isEmpty() ? ImmutableList.copyOf(prTrackingProps.tags()) : ticket.tags();
        String resolvedImpact =
                (ticket.impact() == null || ticket.impact().isBlank()) ? prTrackingProps.impact() : ticket.impact();

        boolean changed = !Objects.equals(ticket.team(), resolvedTeam)
                || !ticket.tags().equals(resolvedTags)
                || !Objects.equals(ticket.impact(), resolvedImpact);
        if (!changed) {
            return ticket;
        }

        return ticketRepository.updateTicket(ticket.toBuilder()
                .team(resolvedTeam)
                .tags(resolvedTags)
                .impact(resolvedImpact)
                .build());
    }

    private @Nullable TicketTeam resolveFirstSuggestedTeam(String authorId) {
        try {
            TicketTeamsSuggestion suggestion =
                    ticketTeamSuggestionsService.getTeamSuggestions("", SlackId.user(authorId));
            String code = !suggestion.userTeams().isEmpty()
                    ? suggestion.userTeams().get(0)
                    : (!suggestion.otherTeams().isEmpty()
                            ? suggestion.otherTeams().get(0)
                            : null);
            return TicketTeam.fromCode(code);
        } catch (RuntimeException e) {
            log.atError()
                    .setCause(e)
                    .addArgument(() -> authorId)
                    .log("Failed to resolve authors team suggestion for Slack user {}, leaving team unchanged");
            return null;
        }
    }

    private void escalateImmediately(PrTrackingRecord tracking, Ticket ticket, String owningTeam) {
        log.atInfo()
                .addArgument(tracking::githubRepo)
                .addArgument(tracking::prNumber)
                .log("PR {}#{} SLA already breached at detection time — escalating immediately");

        Escalation escalation = escalationProcessingService.createEscalation(CreateEscalationRequest.builder()
                .ticket(ticket)
                .team(owningTeam)
                .tags(ImmutableList.of())
                .build());

        if (escalation == null || escalation.id() == null) {
            log.atWarn()
                    .addArgument(tracking::githubRepo)
                    .addArgument(tracking::prNumber)
                    .addArgument(() -> checkNotNull(ticket.id()).id())
                    .log("Escalation creation returned null for PR {}#{} on ticket {}, keeping tracking OPEN");
            return;
        }
        Long escalationId = escalation.id().id();
        prTrackingRepository.updateStatus(
                tracking.id(), com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.ESCALATED, null, escalationId);
        ticketSlackService.markTicketEscalated(ticket.queryRef());
    }

    private void postSlaBreachReply(DetectedPr pr, Duration sla, MessageTs queryTs, String channelId) {
        String text =
                "Pull requests submitted to `%s` are expected to be reviewed within %s. It looks like PR #%d has exceeded that timeframe."
                        .formatted(pr.repositoryName(), formatDuration(sla), pr.pullNumber());
        try {
            slackClient.postMessage(new SlackPostMessageRequest(
                    SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
            log.atInfo()
                    .addArgument(pr::repositoryName)
                    .addArgument(pr::pullNumber)
                    .log("SLA breach message posted for PR {}#{}");
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(pr::repositoryName)
                    .addArgument(pr::pullNumber)
                    .addArgument(queryTs::ts)
                    .log("Failed to post SLA breach message for PR {}#{} in thread {}");
        }
    }

    private void postSlaReply(
            DetectedPr pr, Duration sla, String teamLabel, Instant slaDeadline, MessageTs queryTs, String channelId) {
        String text =
                "Pull requests submitted to `%s` are expected to be reviewed within %s. You don't have to ping us for reviews, but I'll keep an eye on this one. If PR #%d hasn't been reviewed by %s, I'll automatically escalate it to the owning team (%s)."
                        .formatted(
                                pr.repositoryName(),
                                formatDuration(sla),
                                pr.pullNumber(),
                                DEADLINE_FMT.format(slaDeadline),
                                teamLabel);
        try {
            slackClient.postMessage(new SlackPostMessageRequest(
                    SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
            log.atInfo()
                    .addArgument(pr::repositoryName)
                    .addArgument(pr::pullNumber)
                    .log("SLA reply posted for PR {}#{}");
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(pr::repositoryName)
                    .addArgument(pr::pullNumber)
                    .addArgument(queryTs::ts)
                    .log("Failed to post SLA reply for PR {}#{} in thread {}");
        }
    }

    private void addReaction(String emoji, MessageTs queryTs, String channelId) {
        try {
            slackClient.addReaction(ReactionsAddRequest.builder()
                    .name(emoji)
                    .channel(channelId)
                    .timestamp(queryTs.ts())
                    .build());
        } catch (SlackException e) {
            if ("already_reacted".equals(e.getError())) {
                log.atDebug()
                        .addArgument(emoji)
                        .addArgument(queryTs)
                        .log(":{}:  reaction already present on message {}");
            } else {
                log.atWarn()
                        .setCause(e)
                        .addArgument(emoji)
                        .addArgument(queryTs)
                        .addArgument(e::getError)
                        .log("Failed to add :{}: reaction to message {}: {}");
            }
        }
    }

    private String resolveTeamLabel(String teamCode) {
        EscalationTeam team = escalationTeamsRegistry.findEscalationTeamByCode(teamCode);
        return team != null ? team.label() : teamCode;
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = Math.abs(duration.toSeconds());
        if (totalSeconds == 0) {
            return "0 seconds";
        }

        long hours = totalSeconds / 3600;
        long remainder = totalSeconds % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;

        StringBuilder formatted = new StringBuilder();
        appendUnit(formatted, hours, "hour");
        appendUnit(formatted, minutes, "minute");
        appendUnit(formatted, seconds, "second");
        return formatted.toString();
    }

    private static void appendUnit(StringBuilder target, long value, String unit) {
        if (value == 0) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(value).append(' ').append(unit);
        if (value != 1) {
            target.append('s');
        }
    }
}
