package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.PrTrackingRepositoryProps;
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
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

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
    private final SlackClient slackClient;

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
        boolean anyNotOpen = false;

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
            PerPrResult result = processPr(pr, ticket);
            switch (result) {
                case TRACKED -> anyOpenTracked = true;
                case NOT_OPEN -> anyNotOpen = true;
                case SKIPPED -> { }
            }
        }

        // Close only when every linked PR was not open (none were open to track).
        if (anyOpenTracked) {
            return PrDetectionOutcome.tracked();
        }
        if (anyNotOpen) {
            return PrDetectionOutcome.notOpen(prTrackingProps.tags(), prTrackingProps.impact());
        }
        return PrDetectionOutcome.tracked();
    }

    private enum PerPrResult { TRACKED, NOT_OPEN, SKIPPED }

    private PerPrResult processPr(DetectedPr detectedPr, Ticket ticket) {
        PrTrackingRepositoryProps repoConfig = prTrackingProps.repositories().stream()
                .filter(r -> r.name().equals(detectedPr.repositoryName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Repo config not found for " + detectedPr.repositoryName()));

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
                    .log("PR {}#{} is {} — posting notice (ticket will close only when all linked PRs are closed)");
            postPrNotOpenMessage(detectedPr, prMetadata.state(), ticket.queryTs(), ticket.channelId());
            return PerPrResult.NOT_OPEN;
        }

        Instant slaDeadline = prMetadata.createdAt().plus(repoConfig.sla());
        String teamLabel = resolveTeamLabel(repoConfig.owningTeam());
        TicketId ticketId = checkNotNull(ticket.id());

        PrTrackingRecord tracking = prTrackingRepository.insert(new NewPrTracking(
                ticketId.id(),
                detectedPr.repositoryName(),
                detectedPr.pullNumber(),
                prMetadata.createdAt(),
                slaDeadline,
                repoConfig.owningTeam()));

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

    private void escalateImmediately(PrTrackingRecord tracking, Ticket ticket, String owningTeam) {
        log.atInfo()
                .addArgument(tracking::githubRepo)
                .addArgument(tracking::prNumber)
                .log("PR {}#{} SLA already breached at detection time — escalating immediately");

        Escalation escalation = escalationProcessingService.createEscalation(
                CreateEscalationRequest.builder()
                        .ticket(ticket)
                        .team(owningTeam)
                        .tags(ImmutableList.of())
                        .build());

        Long escalationId = escalation != null && escalation.id() != null ? escalation.id().id() : null;
        prTrackingRepository.updateStatus(tracking.id(), com.coreeng.supportbot.dbschema.enums.PrTrackingStatus.ESCALATED, null, escalationId);
        ticketSlackService.markTicketEscalated(ticket.queryRef());
    }

    private void postPrNotOpenMessage(DetectedPr pr, String state, MessageTs queryTs, String channelId) {
        String text = "PR #%d in `%s` seems to already  be `%s`."
                .formatted(pr.pullNumber(), pr.repositoryName(), state);

        slackClient.postMessage(new SlackPostMessageRequest(
                SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
    }

    private void postSlaBreachReply(DetectedPr pr, Duration sla, MessageTs queryTs, String channelId) {
        String text = "Pull requests submitted to `%s` are expected to be reviewed within %s. It looks like PR #%d has exceeded that timeframe."
                .formatted(pr.repositoryName(), formatDuration(sla), pr.pullNumber());

        slackClient.postMessage(new SlackPostMessageRequest(
                SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));

        log.atInfo()
                .addArgument(pr::repositoryName)
                .addArgument(pr::pullNumber)
                .log("SLA breach message posted for PR {}#{}");
    }

    private void postSlaReply(
            DetectedPr pr,
            Duration sla,
            String teamLabel,
            Instant slaDeadline,
            MessageTs queryTs,
            String channelId) {
        String text = "Pull requests submitted to `%s` are expected to be reviewed within %s. You don't have to ping us for reviews, but I'll keep an eye on this one. If PR #%d hasn't been reviewed by %s, I'll automatically escalate it to the owning team (%s)."
                .formatted(pr.repositoryName(), formatDuration(sla), pr.pullNumber(), DEADLINE_FMT.format(slaDeadline), teamLabel);

        slackClient.postMessage(new SlackPostMessageRequest(
                SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));

        log.atInfo()
                .addArgument(pr::repositoryName)
                .addArgument(pr::pullNumber)
                .log("SLA reply posted for PR {}#{}");
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
                        .addArgument(emoji)
                        .addArgument(queryTs)
                        .addArgument(e::getMessage)
                        .log("Failed to add :{}: reaction to message {}: {}");
            }
        }
    }

    private String resolveTeamLabel(String teamCode) {
        EscalationTeam team = escalationTeamsRegistry.findEscalationTeamByCode(teamCode);
        return team != null ? team.label() : teamCode;
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = duration.toSeconds();
        if (totalSeconds % 3600 == 0) {
            long hours = totalSeconds / 3600;
            return hours + " hour" + (hours == 1 ? "" : "s");
        }
        if (totalSeconds % 60 == 0) {
            long minutes = totalSeconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        return totalSeconds + " second" + (totalSeconds == 1 ? "" : "s");
    }
}
