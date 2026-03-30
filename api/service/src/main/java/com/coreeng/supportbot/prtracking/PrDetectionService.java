package com.coreeng.supportbot.prtracking;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.escalation.EscalationSource;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.github.GitHubPullRequestReview;
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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
    private final SlaLookup slaLookup;

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
        Map<String, @Nullable Set<String>> teamReviewerCache = new HashMap<>();
        List<PendingNotification> notifications = new ArrayList<>();
        List<PendingEscalation> pendingEscalations = new ArrayList<>();

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
            boolean canAutoCloseTicket = !event.messageRef().isReply();
            PerPrResult result;
            try {
                result =
                        processPr(pr, ticket, canAutoCloseTicket, teamReviewerCache, notifications, pendingEscalations);
            } catch (Exception e) {
                log.atError()
                        .setCause(e)
                        .addArgument(pr::repositoryName)
                        .addArgument(pr::pullNumber)
                        .log("Failed to process PR {}#{}, skipping");
                continue;
            }
            switch (result) {
                case TRACKED -> {
                    if (!baseReactionsAdded) {
                        addReaction(slackTicketsProps.expectedInitialReaction(), ticket.queryTs(), ticket.channelId());
                        ticketSlackService.markPostTracked(ticket.queryRef());
                        baseReactionsAdded = true;
                    }
                    anyOpenTracked = true;
                    if (!metadataInitialized && !event.messageRef().isReply()) {
                        ticket = initializePrMetadataIfNeeded(ticket, event);
                        metadataInitialized = true;
                    }
                }
                case SKIPPED -> {}
            }
        }

        postNotificationsAndEscalations(notifications, pendingEscalations, ticket.queryTs(), ticket.channelId());

        if (anyOpenTracked) {
            return PrDetectionOutcome.tracked();
        }
        return PrDetectionOutcome.skipped();
    }

    public PrDetectionOutcome handleQueryMessagePosted(MessagePosted event, Supplier<Ticket> ticketSupplier) {
        List<DetectedPr> detectedPrs = prUrlParser.parse(event.message());
        if (detectedPrs.isEmpty()) {
            return PrDetectionOutcome.skipped();
        }

        Ticket ticket = null;
        TicketId ticketId = null;
        boolean anyOpenTracked = false;
        boolean metadataInitialized = false;
        boolean baseReactionsAdded = false;
        Map<String, @Nullable Set<String>> teamReviewerCache = new HashMap<>();
        List<PendingNotification> notifications = new ArrayList<>();
        List<PendingEscalation> pendingEscalations = new ArrayList<>();

        for (DetectedPr pr : detectedPrs) {
            try {
                PrTrackingProps.Repository repoConfig = prTrackingProps.repositories().stream()
                        .filter(r -> r.name().equals(pr.repositoryName()))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalStateException("Repo config not found for " + pr.repositoryName()));

                GitHubPullRequest prMetadata;
                try {
                    prMetadata = gitHubClient.getPullRequest(pr.repositoryName(), pr.pullNumber());
                } catch (GitHubApiException e) {
                    log.atWarn()
                            .addArgument(pr::repositoryName)
                            .addArgument(pr::pullNumber)
                            .addArgument(e::getMessage)
                            .log("Could not fetch PR metadata for {}#{}, skipping: {}");
                    continue;
                }

                if (!prMetadata.isOpen()) {
                    log.atInfo()
                            .addArgument(pr::repositoryName)
                            .addArgument(pr::pullNumber)
                            .addArgument(prMetadata::state)
                            .log("PR {}#{} is {} — skipping tracking");
                    continue;
                }

                if (ticket == null) {
                    ticket = ticketSupplier.get();
                    ticketId = checkNotNull(ticket.id());
                }

                if (prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(
                        checkNotNull(ticketId).id(), pr.repositoryName(), pr.pullNumber())) {
                    log.atInfo()
                            .addArgument(pr::repositoryName)
                            .addArgument(pr::pullNumber)
                            .addArgument(ticketId::id)
                            .log("PR {}#{} already tracked for ticket {}, skipping");
                    continue;
                }

                PerPrResult result = processOpenPr(
                        pr,
                        checkNotNull(ticket),
                        true,
                        repoConfig,
                        prMetadata,
                        teamReviewerCache,
                        notifications,
                        pendingEscalations);
                if (result == PerPrResult.TRACKED) {
                    if (!baseReactionsAdded) {
                        addReaction(slackTicketsProps.expectedInitialReaction(), ticket.queryTs(), ticket.channelId());
                        ticketSlackService.markPostTracked(ticket.queryRef());
                        baseReactionsAdded = true;
                    }
                    anyOpenTracked = true;
                    if (!metadataInitialized && !event.messageRef().isReply()) {
                        ticket = initializePrMetadataIfNeeded(ticket, event);
                        metadataInitialized = true;
                    }
                }
            } catch (Exception e) {
                log.atError()
                        .setCause(e)
                        .addArgument(pr::repositoryName)
                        .addArgument(pr::pullNumber)
                        .log("Failed to process PR {}#{}, skipping");
            }
        }

        if (ticket != null) {
            postNotificationsAndEscalations(notifications, pendingEscalations, ticket.queryTs(), ticket.channelId());
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

    private enum NotificationType {
        TRACKED,
        CHANGES_REQUESTED,
        APPROVED,
        ESCALATED
    }

    private record PendingNotification(
            String repo, int prNumber, NotificationType type, Duration sla, Instant slaDeadline, String teamLabel) {}

    private record PendingEscalation(PrTrackingRecord tracking, Ticket ticket, String owningTeam) {}

    private PerPrResult processPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            Map<String, @Nullable Set<String>> teamReviewerCache,
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations) {
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

        return processOpenPr(
                detectedPr,
                ticket,
                canAutoCloseTicket,
                repoConfig,
                prMetadata,
                teamReviewerCache,
                notifications,
                pendingEscalations);
    }

    private PerPrResult processOpenPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            PrTrackingProps.Repository repoConfig,
            GitHubPullRequest prMetadata,
            Map<String, @Nullable Set<String>> teamReviewerCache,
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations) {

        Duration sla;
        try {
            sla = slaLookup.getSla(repoConfig, detectedPr.repositoryName(), detectedPr.pullNumber());
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .addArgument(e::getMessage)
                    .log("Failed to look up SLA for {}#{}, skipping: {}");
            return PerPrResult.SKIPPED;
        }
        if (sla == null) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log("No SLA found for {}#{}, skipping");
            return PerPrResult.SKIPPED;
        }
        Instant slaDeadline = prMetadata.createdAt().plus(sla);
        String teamLabel = resolveTeamLabel(repoConfig.owningTeam());
        TicketId ticketId = checkNotNull(ticket.id());

        PrTrackingRecord tracking = prTrackingRepository.insertIfAbsent(new NewPrTracking(
                ticketId.id(),
                detectedPr.repositoryName(),
                detectedPr.pullNumber(),
                prMetadata.createdAt(),
                slaDeadline,
                repoConfig.owningTeam(),
                canAutoCloseTicket));
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

        // Evaluate reviews (already fetched with the PR) to determine initial lifecycle state.
        // Note: wall-clock time progresses between the review evaluation and the SLA deadline check
        // below. For deadlines very close to now, remaining duration may go slightly negative;
        // clamping to Duration.ZERO handles this.
        GitHubPullRequestReview latestVerdict = fetchLatestTeamVerdict(prMetadata, repoConfig, teamReviewerCache);

        if (Instant.now().isAfter(slaDeadline)) {
            if (latestVerdict != null && latestVerdict.requestsChanges()) {
                prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.CHANGES_REQUESTED, Duration.ZERO);
                notifications.add(new PendingNotification(
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.CHANGES_REQUESTED,
                        sla,
                        slaDeadline,
                        teamLabel));
            } else if (latestVerdict != null && latestVerdict.isApproved()) {
                prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.APPROVED, Duration.ZERO);
                notifications.add(new PendingNotification(
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.APPROVED,
                        sla,
                        slaDeadline,
                        teamLabel));
            } else {
                notifications.add(new PendingNotification(
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.ESCALATED,
                        sla,
                        slaDeadline,
                        teamLabel));
                pendingEscalations.add(new PendingEscalation(tracking, ticket, repoConfig.owningTeam()));
            }
        } else if (latestVerdict != null && latestVerdict.requestsChanges()) {
            Duration remaining = clampNonNegative(Duration.between(Instant.now(), slaDeadline));
            prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.CHANGES_REQUESTED, remaining);
            notifications.add(new PendingNotification(
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.CHANGES_REQUESTED,
                    sla,
                    slaDeadline,
                    teamLabel));
        } else if (latestVerdict != null && latestVerdict.isApproved()) {
            Duration remaining = clampNonNegative(Duration.between(Instant.now(), slaDeadline));
            prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.APPROVED, remaining);
            notifications.add(new PendingNotification(
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.APPROVED,
                    sla,
                    slaDeadline,
                    teamLabel));
        } else {
            notifications.add(new PendingNotification(
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.TRACKED,
                    sla,
                    slaDeadline,
                    teamLabel));
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

    private @Nullable GitHubPullRequestReview fetchLatestTeamVerdict(
            GitHubPullRequest prMetadata,
            PrTrackingProps.Repository repoConfig,
            Map<String, @Nullable Set<String>> teamReviewerCache) {
        List<GitHubPullRequestReview> teamReviews =
                filterReviewsToOwningTeam(prMetadata.reviews(), prMetadata, repoConfig, teamReviewerCache);
        return teamReviews.stream()
                .filter(r -> r.isApproved() || r.requestsChanges())
                .max(java.util.Comparator.comparing(GitHubPullRequestReview::submittedAt))
                .orElse(null);
    }

    private List<GitHubPullRequestReview> filterReviewsToOwningTeam(
            List<GitHubPullRequestReview> reviews,
            GitHubPullRequest prMetadata,
            PrTrackingProps.Repository repoConfig,
            Map<String, @Nullable Set<String>> cache) {
        // Explicit team slug configured — use Teams API
        if (repoConfig.githubTeamSlug() != null) {
            String org = Iterables.get(Splitter.on('/').split(prMetadata.repositoryName()), 0);
            String teamSlug = repoConfig.githubTeamSlug();
            String cacheKey = org + "/" + teamSlug;
            Set<String> members = resolveCached(cacheKey, cache, () -> {
                try {
                    return Set.copyOf(gitHubClient.resolveTeamReviewers(org, teamSlug));
                } catch (GitHubApiException e) {
                    log.atWarn()
                            .addArgument(repoConfig::githubTeamSlug)
                            .addArgument(e::getMessage)
                            .log("Could not resolve team reviewers for {} at detection — accepting all reviews: {}");
                    return null;
                }
            });
            if (members == null || members.isEmpty()) {
                return reviews;
            }
            return reviews.stream().filter(r -> members.contains(r.userLogin())).toList();
        }

        // No slug — use requested team reviewers already fetched with the PR
        List<String> requestedMembers = prMetadata.requestedTeamReviewerLogins();
        if (requestedMembers.isEmpty()) {
            return reviews;
        }
        Set<String> members = Set.copyOf(requestedMembers);
        return reviews.stream().filter(r -> members.contains(r.userLogin())).toList();
    }

    private @Nullable Set<String> resolveCached(
            String key,
            Map<String, @Nullable Set<String>> cache,
            java.util.function.Supplier<@Nullable Set<String>> loader) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Set<String> result = loader.get();
        cache.put(key, result);
        return result;
    }

    private void postNotificationsAndEscalations(
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations,
            MessageTs queryTs,
            String channelId) {
        Map<String, List<PendingNotification>> notifsByRepo = new LinkedHashMap<>();
        for (PendingNotification n : notifications) {
            notifsByRepo.computeIfAbsent(n.repo(), k -> new ArrayList<>()).add(n);
        }

        Map<String, List<PendingEscalation>> escalationsByRepo = new LinkedHashMap<>();
        for (PendingEscalation e : pendingEscalations) {
            escalationsByRepo
                    .computeIfAbsent(e.tracking().githubRepo(), k -> new ArrayList<>())
                    .add(e);
        }

        // Merge repo keys preserving insertion order
        Set<String> allRepos = new java.util.LinkedHashSet<>();
        allRepos.addAll(notifsByRepo.keySet());
        allRepos.addAll(escalationsByRepo.keySet());

        for (String repo : allRepos) {
            try {
                List<PendingNotification> repoNotifs = notifsByRepo.getOrDefault(repo, List.of());
                if (repoNotifs.size() == 1) {
                    postSingleNotification(repoNotifs.getFirst(), queryTs, channelId);
                } else if (repoNotifs.size() > 1) {
                    postGroupedNotifications(repoNotifs, queryTs, channelId);
                }

                for (PendingEscalation e : escalationsByRepo.getOrDefault(repo, List.of())) {
                    escalateImmediately(e.tracking(), e.ticket(), e.owningTeam());
                }
            } catch (Exception e) {
                log.atError()
                        .setCause(e)
                        .addArgument(() -> repo)
                        .log("Failed to post notifications for repo {}, continuing with next repo");
            }
        }
    }

    private void postSingleNotification(PendingNotification n, MessageTs queryTs, String channelId) {
        String text =
                switch (n.type()) {
                    case TRACKED ->
                        "Pull requests submitted to `%s` are expected to be reviewed within %s. You don't have to ping us for reviews, but I'll keep an eye on this one. If <%s|PR #%d> hasn't been reviewed by %s, I'll automatically escalate it to the owning team (%s)."
                                .formatted(
                                        n.repo(),
                                        formatDuration(n.sla()),
                                        prUrl(n.repo(), n.prNumber()),
                                        n.prNumber(),
                                        DEADLINE_FMT.format(n.slaDeadline()),
                                        n.teamLabel());
                    case CHANGES_REQUESTED ->
                        "<%s|PR #%d> for `%s` has been reviewed and changes have been requested. :eyes:"
                                .formatted(prUrl(n.repo(), n.prNumber()), n.prNumber(), n.repo());
                    case APPROVED ->
                        "<%s|PR #%d> for `%s` has been approved and is ready to merge. :white_check_mark:"
                                .formatted(prUrl(n.repo(), n.prNumber()), n.prNumber(), n.repo());
                    case ESCALATED ->
                        "Pull requests submitted to `%s` are expected to be reviewed within %s. It looks like <%s|PR #%d> has exceeded that timeframe."
                                .formatted(
                                        n.repo(), formatDuration(n.sla()), prUrl(n.repo(), n.prNumber()), n.prNumber());
                };
        postText(text, n.repo(), n.prNumber(), n.type(), queryTs, channelId);
    }

    private void postGroupedNotifications(List<PendingNotification> repoNotifs, MessageTs queryTs, String channelId) {
        String repo = repoNotifs.getFirst().repo();

        Map<NotificationType, List<PendingNotification>> byType = new LinkedHashMap<>();
        for (PendingNotification n : repoNotifs) {
            byType.computeIfAbsent(n.type(), k -> new ArrayList<>()).add(n);
        }

        for (var entry : byType.entrySet()) {
            NotificationType type = entry.getKey();
            List<PendingNotification> group = entry.getValue();

            if (group.size() == 1) {
                postSingleNotification(group.getFirst(), queryTs, channelId);
                continue;
            }

            String prList = group.stream()
                    .map(n -> "<%s|#%d>".formatted(prUrl(n.repo(), n.prNumber()), n.prNumber()))
                    .collect(Collectors.joining(", "));

            String text =
                    switch (type) {
                        case TRACKED -> formatTrackedGroup(repo, group, prList);
                        case CHANGES_REQUESTED ->
                            "PRs %s for `%s` have been reviewed and changes have been requested. :eyes:"
                                    .formatted(prList, repo);
                        case APPROVED ->
                            "PRs %s for `%s` have been approved and are ready to merge. :white_check_mark:"
                                    .formatted(prList, repo);
                        case ESCALATED ->
                            "PRs %s for `%s` are expected to be reviewed within %s. They have exceeded that timeframe — escalating. :rocket:"
                                    .formatted(
                                            prList,
                                            repo,
                                            formatDuration(group.getFirst().sla()));
                    };
            postText(text, repo, 0, type, queryTs, channelId);
        }
    }

    private String formatTrackedGroup(String repo, List<PendingNotification> group, String prList) {
        String teamLabel = group.getFirst().teamLabel();
        boolean sameSla =
                group.stream().map(PendingNotification::slaDeadline).distinct().count() == 1;

        if (sameSla) {
            Instant deadline = group.getFirst().slaDeadline();
            Duration sla = group.getFirst().sla();
            return ("I'm tracking PRs %s for `%s`. Pull requests are expected to be reviewed within %s. "
                            + "You don't have to ping for reviews — I'll keep an eye on these. "
                            + "If not reviewed by %s, I'll automatically escalate to the owning team (%s).")
                    .formatted(prList, repo, formatDuration(sla), DEADLINE_FMT.format(deadline), teamLabel);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("I'm tracking PRs for `%s`. You don't have to ping for reviews — I'll keep an eye on these:\n"
                    .formatted(repo));
            for (PendingNotification n : group) {
                sb.append("- <%s|#%d> — review by %s\n"
                        .formatted(prUrl(n.repo(), n.prNumber()), n.prNumber(), DEADLINE_FMT.format(n.slaDeadline())));
            }
            sb.append("If not reviewed by their deadline, I'll escalate to the owning team (%s).".formatted(teamLabel));
            return sb.toString();
        }
    }

    private static String prUrl(String repo, int prNumber) {
        return "https://github.com/%s/pull/%d".formatted(repo, prNumber);
    }

    private void postText(
            String text, String repo, int prNumber, NotificationType type, MessageTs queryTs, String channelId) {
        try {
            slackClient.postMessage(new SlackPostMessageRequest(
                    SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
            log.atInfo().addArgument(() -> repo).addArgument(() -> type).log("Notification posted for {} ({})");
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(() -> type)
                    .addArgument(() -> repo)
                    .addArgument(() -> prNumber)
                    .log("Failed to post {} notification for {}#{}, continuing");
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
                .source(EscalationSource.bot)
                .build());

        if (escalation == null || escalation.id() == null) {
            log.atWarn()
                    .addArgument(tracking::githubRepo)
                    .addArgument(tracking::prNumber)
                    .addArgument(() -> checkNotNull(ticket.id()).id())
                    .log(
                            "Escalation creation returned null for PR {}#{} on ticket {} — marking tracking ESCALATED to avoid reprocessing");
            prTrackingRepository.updateStatus(tracking.id(), PrTrackingStatus.ESCALATED, null, null);
            return;
        }
        Long escalationId = escalation.id().id();
        prTrackingRepository.updateStatus(tracking.id(), PrTrackingStatus.ESCALATED, null, escalationId);
        ticketSlackService.markTicketEscalated(ticket.queryRef());
    }

    private static Duration clampNonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
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

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long remainder = totalSeconds % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;

        StringBuilder formatted = new StringBuilder();
        appendUnit(formatted, days, "day");
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
