package com.coreeng.supportbot.prtracking;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.escalation.EscalationSource;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.github.GitHubPullRequestReview;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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

        Map<String, @Nullable Set<String>> teamMemberCache = new HashMap<>();

        for (PrTrackingRecord record : active) {
            try {
                processRecord(record, teamMemberCache);
            } catch (Exception e) {
                log.atError()
                        .addArgument(record::githubRepo)
                        .addArgument(record::prNumber)
                        .setCause(e)
                        .log("Error processing PR tracking record for {}#{}, continuing with next record");
            }
        }
    }

    private void processRecord(PrTrackingRecord record, Map<String, @Nullable Set<String>> teamMemberCache) {
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
            handlePrClosed(record, pr);
            return;
        }

        List<GitHubPullRequestReview> reviews;
        try {
            reviews = gitHubClient.listReviews(record.githubRepo(), record.prNumber());
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .addArgument(e::getMessage)
                    .log("Could not fetch reviews for PR {}#{}: {}");
            return;
        }

        List<GitHubPullRequestReview> teamReviews = filterToOwningTeam(record, reviews, teamMemberCache);
        GitHubPullRequestReview latestVerdict = findLatestActionableReview(teamReviews);

        switch (record.status()) {
            case OPEN -> processOpenRecord(record, pr, latestVerdict);
            case CHANGES_REQUESTED -> processChangesRequestedRecord(record, pr, latestVerdict);
            case APPROVED -> processApprovedRecord(record, pr, latestVerdict);
            case ESCALATED -> processEscalatedRecord(record, pr, latestVerdict);
            default -> log.atWarn().addArgument(record::status).log("Unexpected active record status: {}");
        }

        updateActivityTimestamps(record, teamReviews);
    }

    private List<GitHubPullRequestReview> filterToOwningTeam(
            PrTrackingRecord record, List<GitHubPullRequestReview> reviews, Map<String, @Nullable Set<String>> cache) {
        Set<String> teamMembers = resolveOwningTeamMembers(record, cache);

        if (teamMembers == null || teamMembers.isEmpty()) {
            return reviews;
        }

        return reviews.stream().filter(r -> teamMembers.contains(r.userLogin())).toList();
    }

    private @Nullable Set<String> resolveOwningTeamMembers(
            PrTrackingRecord record, Map<String, @Nullable Set<String>> cache) {
        // Tier 1: explicit GitHub team slug in config
        PrTrackingProps.Repository repoConfig = findRepoConfig(record.githubRepo());
        if (repoConfig != null && repoConfig.githubTeamSlug() != null) {
            String org = Iterables.get(Splitter.on('/').split(record.githubRepo()), 0);
            return resolveTeamReviewers(org, repoConfig.githubTeamSlug(), cache);
        }

        // Tier 2: requested team reviewers on the PR.
        // If neither tier resolves members (empty = no teams requested, null = API failure),
        // all reviews are accepted without filtering.
        String cacheKey = "requested:" + record.githubRepo() + "#" + record.prNumber();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        try {
            List<String> requestedMembers =
                    gitHubClient.resolveRequestedReviewers(record.githubRepo(), record.prNumber());
            Set<String> members = requestedMembers.isEmpty() ? Set.of() : Set.copyOf(requestedMembers);
            cache.put(cacheKey, members);
            return members;
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .addArgument(e::getMessage)
                    .log("Could not fetch requested teams for PR {}#{} — skipping team validation: {}");
            cache.put(cacheKey, null);
            return null;
        }
    }

    private @Nullable Set<String> resolveTeamReviewers(
            String org, String teamSlug, Map<String, @Nullable Set<String>> cache) {
        String key = org + "/" + teamSlug;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        try {
            Set<String> members = Set.copyOf(gitHubClient.resolveTeamReviewers(org, teamSlug));
            cache.put(key, members);
            return members;
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(() -> org + "/" + teamSlug)
                    .addArgument(e::getMessage)
                    .log("Could not fetch team members for {} — skipping team validation: {}");
            cache.put(key, null);
            return null;
        }
    }

    private PrTrackingProps.@Nullable Repository findRepoConfig(String githubRepo) {
        return prTrackingProps.repositories().stream()
                .filter(r -> r.name().equalsIgnoreCase(githubRepo))
                .findFirst()
                .orElse(null);
    }

    private @Nullable GitHubPullRequestReview findLatestActionableReview(List<GitHubPullRequestReview> reviews) {
        return reviews.stream()
                .filter(r -> r.isApproved() || r.requestsChanges())
                .max(Comparator.comparing(GitHubPullRequestReview::submittedAt))
                .orElse(null);
    }

    // OPEN → CHANGES_REQUESTED | APPROVED | CLOSED (approved+mergeable) | ESCALATED (SLA breach) | no-op
    private void processOpenRecord(
            PrTrackingRecord record, GitHubPullRequest pr, @Nullable GitHubPullRequestReview latestVerdict) {
        if (latestVerdict != null && latestVerdict.requestsChanges()) {
            Duration remaining = computeRemainingDuration(record);
            if (remaining != null) {
                prTrackingRepository.pauseSla(record.id(), PrTrackingStatus.CHANGES_REQUESTED, remaining);
                notifyChangesRequested(record);
            }
        } else if (latestVerdict != null && latestVerdict.isApproved()) {
            handleApproval(record, pr);
        } else if (record.slaDeadline() != null && Instant.now().isAfter(record.slaDeadline())) {
            handleSlaBreached(record);
        }
    }

    // CHANGES_REQUESTED → APPROVED | CLOSED (approved+mergeable) | OPEN (no actionable reviews remain)
    private void processChangesRequestedRecord(
            PrTrackingRecord record, GitHubPullRequest pr, @Nullable GitHubPullRequestReview latestVerdict) {
        if (latestVerdict != null && latestVerdict.isApproved()) {
            handleApproval(record, pr);
        } else if (latestVerdict == null) {
            // No actionable reviews remain (all dismissed or retracted) — resume SLA
            resumeSlaToOpen(record);
        }
    }

    // APPROVED → CLOSED (mergeable) | CHANGES_REQUESTED (re-review) | no-op (awaiting merge)
    private void processApprovedRecord(
            PrTrackingRecord record, GitHubPullRequest pr, @Nullable GitHubPullRequestReview latestVerdict) {
        if (pr.isMergeable()) {
            handleApprovalClosure(record);
        } else if (latestVerdict != null && latestVerdict.requestsChanges()) {
            prTrackingRepository.updateStatus(
                    record.id(), PrTrackingStatus.CHANGES_REQUESTED, null, record.escalationId());
            notifyChangesRequested(record);
        }
    }

    // ESCALATED → APPROVED | CLOSED (approved+mergeable) | CHANGES_REQUESTED
    private void processEscalatedRecord(
            PrTrackingRecord record, GitHubPullRequest pr, @Nullable GitHubPullRequestReview latestVerdict) {
        if (latestVerdict != null && latestVerdict.isApproved()) {
            if (pr.isMergeable()) {
                handleApprovalClosure(record);
            } else {
                prTrackingRepository.updateStatus(record.id(), PrTrackingStatus.APPROVED, null, record.escalationId());
                log.atInfo()
                        .addArgument(record::githubRepo)
                        .addArgument(record::prNumber)
                        .log("PR {}#{} approved after escalation — awaiting merge");
            }
        } else if (latestVerdict != null && latestVerdict.requestsChanges()) {
            prTrackingRepository.updateStatus(
                    record.id(), PrTrackingStatus.CHANGES_REQUESTED, null, record.escalationId());
            notifyChangesRequested(record);
        }
    }

    // Called from OPEN and CHANGES_REQUESTED — pauses SLA when OPEN, just updates status otherwise
    private void handleApproval(PrTrackingRecord record, GitHubPullRequest pr) {
        if (pr.isMergeable()) {
            handleApprovalClosure(record);
        } else if (record.status() == PrTrackingStatus.OPEN) {
            Duration remaining = computeRemainingDuration(record);
            if (remaining == null) {
                return;
            }
            prTrackingRepository.pauseSla(record.id(), PrTrackingStatus.APPROVED, remaining);
            log.atInfo()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .log("PR {}#{} approved — SLA paused, awaiting merge");
        } else {
            prTrackingRepository.updateStatus(record.id(), PrTrackingStatus.APPROVED, null, record.escalationId());
            log.atInfo()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .log("PR {}#{} approved — awaiting merge");
        }
    }

    private void handlePrClosed(PrTrackingRecord record, GitHubPullRequest pr) {
        String action = pr.state() == GitHubPullRequest.PrState.MERGED ? "merged" : "closed";
        closeRecordAndNotify(
                record,
                "PR `%s#%d` has been %s. :white_check_mark:".formatted(record.githubRepo(), record.prNumber(), action));
    }

    private void notifyChangesRequested(PrTrackingRecord record) {
        log.atInfo()
                .addArgument(record::githubRepo)
                .addArgument(record::prNumber)
                .log("PR {}#{} changes requested");

        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn()
                    .addArgument(record::ticketId)
                    .log("Ticket {} not found for changes-requested notification, skipping Slack message");
            return;
        }

        postMessage(
                "PR `%s#%d` has been reviewed and changes have been requested."
                        .formatted(record.githubRepo(), record.prNumber()),
                ticket.channelId(),
                ticket.queryTs(),
                record);
    }

    private void handleApprovalClosure(PrTrackingRecord record) {
        closeRecordAndNotify(
                record,
                "PR `%s#%d` has been approved and is ready to merge. :white_check_mark:"
                        .formatted(record.githubRepo(), record.prNumber()));
    }

    private void closeRecordAndNotify(PrTrackingRecord record, String message) {
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

        postMessage(message, ticket.channelId(), ticket.queryTs(), record);

        if (!record.canAutoCloseTicket()) {
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
                .source(EscalationSource.bot)
                .build());

        if (escalation == null || escalation.id() == null) {
            log.atWarn()
                    .addArgument(record::githubRepo)
                    .addArgument(record::prNumber)
                    .addArgument(record::ticketId)
                    .log(
                            "Escalation creation returned null for PR {}#{} on ticket {} — marking tracking ESCALATED to avoid reprocessing");
            prTrackingRepository.updateStatus(record.id(), PrTrackingStatus.ESCALATED, null, null);
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

    private void updateActivityTimestamps(PrTrackingRecord record, List<GitHubPullRequestReview> teamReviews) {
        Instant latestReviewAt = teamReviews.stream()
                .map(GitHubPullRequestReview::submittedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (latestReviewAt != null
                && (record.lastReviewAt() == null || latestReviewAt.isAfter(record.lastReviewAt()))) {
            prTrackingRepository.updateActivityTimestamps(record.id(), latestReviewAt, record.lastAuthorActivityAt());
        }
    }

    private void resumeSlaToOpen(PrTrackingRecord record) {
        if (record.slaRemaining() == null) {
            log.atWarn().addArgument(record::id).log("Cannot resume SLA for record {} — no remaining duration stored");
            return;
        }
        Instant newDeadline = Instant.now().plus(record.slaRemaining());
        prTrackingRepository.resumeSla(record.id(), newDeadline);
        log.atInfo()
                .addArgument(record::githubRepo)
                .addArgument(record::prNumber)
                .log("PR {}#{} — SLA resumed");
    }

    private @Nullable Duration computeRemainingDuration(PrTrackingRecord record) {
        if (record.slaDeadline() == null) {
            log.atWarn()
                    .addArgument(record::id)
                    .log("Record {} has no SLA deadline — cannot compute remaining duration");
            return null;
        }
        Duration remaining = Duration.between(Instant.now(), record.slaDeadline());
        return remaining.isNegative() ? Duration.ZERO : remaining;
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
                    .log("Failed to post Slack message for PR {}#{} on ticket {}, continuing");
        }
    }
}
