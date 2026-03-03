package com.coreeng.supportbot.prtracking;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PrLifecyclePoller {

    private final PrTrackingRepository prTrackingRepository;
    private final GitHubClient gitHubClient;
    private final TicketRepository ticketRepository;
    private final TicketProcessingService ticketProcessingService;
    private final EscalationProcessingService escalationProcessingService;
    private final TicketSlackService ticketSlackService;
    private final SlackClient slackClient;
    private final PrTrackingProps prTrackingProps;

    @Scheduled(cron = "${pr-review-tracking.poll-cron:0 0 9-18 * * 1-5}")
    public void poll() {
        List<PrTrackingRecord> active = prTrackingRepository.findAllActive();
        log.atInfo().addArgument(active::size).log("PR lifecycle poll: {} active records");

        for (PrTrackingRecord record : active) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.atError()
                        .addArgument(record::githubRepo)
                        .addArgument(record::prNumber)
                        .setCause(e)
                        .log("Error processing PR tracking record for {}#{}, continuing with next record");
            }
        }
    }

    private void processRecord(PrTrackingRecord record) {
        GitHubPullRequest pr;
        try {
            pr = gitHubClient.getPullRequest(record.githubRepo(), record.prNumber());
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .addArgument(e::getMessage)
                    .log("Could not fetch PR {}#{}: {}");
            return;
        }

        if (pr.isClosed()) {
            handlePrClosed(record);
        } else if (record.status() == PrTrackingStatus.OPEN && Instant.now().isAfter(record.slaDeadline())) {
            handleSlaBreached(record);
        }
    }

    private void handlePrClosed(PrTrackingRecord record) {
        prTrackingRepository.updateStatus(record.id(), PrTrackingStatus.CLOSED, Instant.now(), record.escalationId());
        log.atInfo()
                .addArgument(record::githubRepo)
                .addArgument(record::prNumber)
                .log("PR {}#{} marked as CLOSED");

        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn()
                    .addArgument(record::ticketId)
                    .log("Ticket {} not found after PR closed, skipping Slack message");
            return;
        }

        postMessage(
                "PR `%s#%d` has been closed. :white_check_mark:".formatted(record.githubRepo(), record.prNumber()),
                ticket.channelId(),
                ticket.queryTs(),
                record);

        if (!record.closeTicketOnResolve()) {
            log.atInfo()
                    .addArgument(record::ticketId)
                    .log("PR for ticket {} resolved from thread reply context; skipping auto-close");
            return;
        }

        if (!prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId())) {
            log.atInfo().addArgument(record::ticketId).log("All PRs resolved for ticket {}, closing ticket");
            ticketProcessingService.closeForPrResolution(
                    checkNotNull(ticket.id()), ImmutableList.copyOf(prTrackingProps.tags()), prTrackingProps.impact());
        }
    }

    private void handleSlaBreached(PrTrackingRecord record) {
        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn().addArgument(record::ticketId).log("Ticket {} not found for SLA breach, skipping");
            return;
        }

        Escalation escalation = escalationProcessingService.createEscalation(CreateEscalationRequest.builder()
                .ticket(ticket)
                .team(record.owningTeam())
                .tags(ImmutableList.of())
                .build());

        if (escalation == null || escalation.id() == null) {
            log.atWarn()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .addArgument(record::ticketId)
                    .log("Escalation creation returned null for PR {}#{} on ticket {}, keeping tracking OPEN");
            return;
        }
        Long escalationId = escalation.id().id();
        prTrackingRepository.updateStatus(record.id(), PrTrackingStatus.ESCALATED, null, escalationId);
        ticketSlackService.markTicketEscalated(ticket.queryRef());

        log.atInfo()
                .addArgument(record::githubRepo)
                .addArgument(record::prNumber)
                .addArgument(record::ticketId)
                .log("PR {}#{} SLA breached — escalated on ticket {}");
    }

    private void postMessage(String text, String channelId, MessageTs queryTs, PrTrackingRecord record) {
        try {
            slackClient.postMessage(new SlackPostMessageRequest(
                    SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .addArgument(record::ticketId)
                    .log("Failed to post closure message for PR {}#{} on ticket {}, continuing");
        }
    }
}
