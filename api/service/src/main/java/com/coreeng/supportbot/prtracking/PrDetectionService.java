package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.PrTrackingRepositoryProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
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
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
    private final SlackClient slackClient;

    public boolean containsPrLinks(String message) {
        return !prUrlParser.parse(message).isEmpty();
    }

    public void handleMessagePosted(MessagePosted event, Ticket ticket) {
        List<DetectedPr> detectedPrs = prUrlParser.parse(event.message());
        if (detectedPrs.isEmpty()) {
            return;
        }

        TicketId ticketId = checkNotNull(ticket.id());
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
            processPr(pr, ticket);
        }
    }

    private void processPr(DetectedPr detectedPr, Ticket ticket) {
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
            return;
        }

        Instant slaDeadline = prMetadata.createdAt().plus(repoConfig.sla());
        String teamLabel = resolveTeamLabel(repoConfig.owningTeam());
        TicketId ticketId = checkNotNull(ticket.id());

        prTrackingRepository.insert(new NewPrTracking(
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

        postSlaReply(detectedPr, repoConfig.sla(), teamLabel, slaDeadline, queryTs, channelId);
        addReaction("pr", queryTs, channelId);
    }

    private void postSlaReply(
            DetectedPr pr,
            Duration sla,
            String teamLabel,
            Instant slaDeadline,
            MessageTs queryTs,
            String channelId) {
        String text = "PRs against `%s` have an SLA of %s. I'll automatically escalate to the owning team (%s) if PR #%d is not reviewed before %s."
                .formatted(pr.repositoryName(), formatDuration(sla), teamLabel, pr.pullNumber(), DEADLINE_FMT.format(slaDeadline));

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
        @Nullable EscalationTeam team = escalationTeamsRegistry.findEscalationTeamByCode(teamCode);
        return team != null ? team.label() : teamCode;
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours > 0 && duration.toMinutes() % 60 == 0) {
            return hours + " hour" + (hours == 1 ? "" : "s");
        }
        long minutes = duration.toMinutes();
        if (minutes > 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        return duration.toString();
    }
}
