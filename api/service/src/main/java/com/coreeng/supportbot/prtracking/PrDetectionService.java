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
import com.coreeng.supportbot.prtracking.source.PrMetadata;
import com.coreeng.supportbot.prtracking.source.PrSourceClients;
import com.coreeng.supportbot.prtracking.source.PrSourceException;
import com.coreeng.supportbot.prtracking.source.Provider;
import com.coreeng.supportbot.prtracking.source.RepoCoord;
import com.coreeng.supportbot.prtracking.source.Review;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Service
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PrDetectionService {

    private static final DateTimeFormatter DEADLINE_FMT =
            DateTimeFormatter.ofPattern("EEE dd MMM 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final PrUrlDispatcher prUrlDispatcher;
    private final PrSourceClients prSourceClients;
    private final TeamReviewFilter teamReviewFilter;
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
    private final PrMessageRenderer messageRenderer;
    private final PrUrlResolver prUrlResolver;

    public boolean containsPrLinks(String message) {
        return !prUrlDispatcher.parse(message).isEmpty();
    }

    public PrDetectionOutcome handleMessagePosted(MessagePosted event, Ticket ticket) {
        List<DetectedPr> detectedPrs = prUrlDispatcher.parse(event.message());
        if (detectedPrs.isEmpty()) {
            return PrDetectionOutcome.skipped();
        }

        TicketId ticketId = checkNotNull(ticket.id());
        boolean anyOpenTracked = false;
        boolean metadataInitialized = false;
        boolean baseReactionsAdded = false;
        Map<String, Optional<Set<String>>> teamReviewerCache = new HashMap<>();
        List<PendingNotification> notifications = new ArrayList<>();
        List<PendingEscalation> pendingEscalations = new ArrayList<>();

        for (DetectedPr pr : detectedPrs) {
            if (prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(
                    ticketId.id(), pr.provider(), pr.repositoryName(), pr.pullNumber())) {
                log.atInfo()
                        .addArgument(pr::repositoryName)
                        .addArgument(pr::pullNumber)
                        .addArgument(ticketId::id)
                        .log("PR {}#{} already tracked for ticket {}, skipping");
                continue;
            }

            PrMetadata prMetadata;
            try {
                prMetadata = prSourceClients
                        .forProvider(pr.provider())
                        .fetchPullRequest(new RepoCoord(pr.provider(), pr.repositoryName()), pr.pullNumber());
            } catch (PrSourceException e) {
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

            boolean canAutoCloseTicket = !event.messageRef().isReply();
            PerPrResult result;
            try {
                result = processPr(
                        pr,
                        ticket,
                        canAutoCloseTicket,
                        prMetadata,
                        teamReviewerCache,
                        notifications,
                        pendingEscalations);
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
        List<DetectedPr> detectedPrs = prUrlDispatcher.parse(event.message());
        if (detectedPrs.isEmpty()) {
            return PrDetectionOutcome.skipped();
        }

        Ticket ticket = null;
        TicketId ticketId = null;
        boolean anyOpenTracked = false;
        boolean metadataInitialized = false;
        boolean baseReactionsAdded = false;
        Map<String, Optional<Set<String>>> teamReviewerCache = new HashMap<>();
        List<PendingNotification> notifications = new ArrayList<>();
        List<PendingEscalation> pendingEscalations = new ArrayList<>();

        for (DetectedPr pr : detectedPrs) {
            try {

                PrMetadata prMetadata;
                try {
                    prMetadata = prSourceClients
                            .forProvider(pr.provider())
                            .fetchPullRequest(new RepoCoord(pr.provider(), pr.repositoryName()), pr.pullNumber());
                } catch (PrSourceException e) {
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
                        checkNotNull(ticketId).id(), pr.provider(), pr.repositoryName(), pr.pullNumber())) {
                    log.atInfo()
                            .addArgument(pr::repositoryName)
                            .addArgument(pr::pullNumber)
                            .addArgument(ticketId::id)
                            .log("PR {}#{} already tracked for ticket {}, skipping");
                    continue;
                }

                PerPrResult result =
                        processPr(pr, ticket, true, prMetadata, teamReviewerCache, notifications, pendingEscalations);

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
        TRACKED(true),
        NO_SLA_TRACKED(false),
        CHANGES_REQUESTED(false),
        APPROVED(false),
        ESCALATED(true);

        private final boolean requiresSla;

        NotificationType(boolean requiresSla) {
            this.requiresSla = requiresSla;
        }

        boolean requiresSla() {
            return requiresSla;
        }
    }

    private record PendingNotification(
            Provider provider,
            String repo,
            int prNumber,
            NotificationType type,
            @Nullable Duration sla,
            @Nullable Instant slaDeadline,
            @Nullable String teamLabel) {
        PendingNotification {
            checkNotNull(provider);
            checkNotNull(repo);
            checkNotNull(type);
            if (type.requiresSla()) {
                checkNotNull(sla, "sla required for %s", type);
                checkNotNull(slaDeadline, "slaDeadline required for %s", type);
            }
            checkNotNull(teamLabel, "teamLabel required for all notification types");
        }
    }

    private record PendingEscalation(PrTrackingRecord tracking, Ticket ticket) {
        PendingEscalation {
            checkNotNull(tracking);
            checkNotNull(ticket);
        }
    }

    private PerPrResult processPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            PrMetadata prMetadata,
            Map<String, Optional<Set<String>>> teamReviewerCache,
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations) {

        Optional<PrTrackingProps.Repository> repoConfig = prTrackingProps.repositories().stream()
                .filter(r -> r.name().equals(detectedPr.repositoryName()))
                .findFirst();

        if (repoConfig.isPresent()) {
            // Author admission gate (PT-521): runs before any tracking record or Slack side effect.
            if (!authorAllowed(detectedPr, repoConfig.get(), prMetadata, teamReviewerCache)) {
                return PerPrResult.SKIPPED;
            }
            // Repo is configured for PR tracking with or without SLA
            if (repoConfig.get().hasNoSla()) {
                // No-SLA tracking: track by path filter without a deadline or escalation.
                return processNoSlaOpenPr(
                        detectedPr,
                        ticket,
                        canAutoCloseTicket,
                        repoConfig.get(),
                        prMetadata,
                        teamReviewerCache,
                        notifications);
            } else {
                return processOpenPr(
                        detectedPr,
                        ticket,
                        canAutoCloseTicket,
                        repoConfig.get(),
                        prMetadata,
                        teamReviewerCache,
                        notifications,
                        pendingEscalations);
            }
        } else {
            log.atInfo()
                    .addArgument(detectedPr::repositoryName)
                    .log("Repo {} is not configured for PR tracking, skipping");
            return PerPrResult.SKIPPED;
        }
    }

    /**
     * Author admission gate (PT-521). When a repo configures {@code allowed-author-teams}, only
     * PRs/MRs whose author belongs to at least one of those teams are tracked; everyone else is
     * skipped before a tracking record (or any Slack side effect) is created.
     *
     * <p>Fails open — we never silently drop a PR because of a transient lookup problem. Tracking
     * proceeds when no allow-list is configured, when the provider returned no author, or when team
     * membership could not be fully resolved; we skip only when every configured team resolved and
     * the author was in none of them. Membership is any-of, and GitLab inherited/invited-group
     * members count (resolution uses {@code /members/all}). Mirrors {@link TeamReviewFilter}'s
     * graceful-degradation stance.
     */
    private boolean authorAllowed(
            DetectedPr detectedPr,
            PrTrackingProps.Repository repoConfig,
            PrMetadata prMetadata,
            Map<String, Optional<Set<String>>> teamReviewerCache) {
        List<String> allowedTeams = repoConfig.allowedAuthorTeams();
        if (allowedTeams.isEmpty()) {
            return true;
        }
        String author = prMetadata.authorLogin();
        if (author == null) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log("PR {}#{} has no resolved author but allowed-author-teams is configured — tracking anyway");
            return true;
        }
        RepoCoord coord = new RepoCoord(detectedPr.provider(), detectedPr.repositoryName());
        boolean allResolved = true;
        for (String team : allowedTeams) {
            Set<String> members = teamReviewFilter.resolveTeamMembers(coord, team, teamReviewerCache);
            if (members == null) {
                allResolved = false;
                continue;
            }
            if (members.contains(author)) {
                return true;
            }
        }
        if (!allResolved) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log("PR {}#{} allowed-author-teams membership could not be fully resolved — tracking anyway");
            return true;
        }
        log.atInfo()
                .addArgument(detectedPr::repositoryName)
                .addArgument(detectedPr::pullNumber)
                .addArgument(() -> author)
                .log("PR {}#{} author {} is not in any allowed-author-team — skipping tracking");
        return false;
    }

    private PerPrResult processOpenPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            PrTrackingProps.Repository repoConfig,
            PrMetadata prMetadata,
            Map<String, Optional<Set<String>>> teamReviewerCache,
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations) {

        Duration sla;
        try {
            sla = slaLookup.getSla(
                    repoConfig,
                    new RepoCoord(detectedPr.provider(), detectedPr.repositoryName()),
                    detectedPr.pullNumber());
        } catch (PrSourceException e) {
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
                detectedPr.provider(),
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
        List<Review> teamReviews =
                teamReviewFilter.filterToOwningTeam(prMetadata.reviews(), prMetadata, repoConfig, teamReviewerCache);
        Review latestVerdict = teamReviewFilter.findLatestActionableReview(teamReviews);

        if (Instant.now().isAfter(slaDeadline)) {
            if (latestVerdict != null && latestVerdict.requestsChanges()) {
                prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.CHANGES_REQUESTED, Duration.ZERO);
                notifications.add(new PendingNotification(
                        detectedPr.provider(),
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.CHANGES_REQUESTED,
                        sla,
                        slaDeadline,
                        teamLabel));
            } else if (latestVerdict != null && latestVerdict.isApproved()) {
                prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.APPROVED, Duration.ZERO);
                notifications.add(new PendingNotification(
                        detectedPr.provider(),
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.APPROVED,
                        sla,
                        slaDeadline,
                        teamLabel));
            } else {
                // Post the notification synchronously before escalating. The tracking record is
                // now visible to the poller (status=OPEN, SLA already breached), so if we deferred
                // both steps the poller could fire between the insert and postNotificationsAndEscalations,
                // posting the escalation card before our notification arrives in the thread.
                PrMessageContext breachCtx = new PrMessageContext(
                        detectedPr.provider(),
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        repoConfig.owningTeam(),
                        sla,
                        slaDeadline);
                String override =
                        messageRenderer.render(detectedPr.repositoryName(), MessageEvent.ESCALATED, breachCtx);
                String breachText = override != null
                        ? override
                        : formatEscalatedText(
                                detectedPr.provider(), detectedPr.repositoryName(), detectedPr.pullNumber(), sla);
                postText(
                        breachText,
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.ESCALATED,
                        ticket.queryTs(),
                        ticket.channelId());
                escalateImmediately(tracking, ticket, repoConfig.owningTeam());
            }
        } else if (latestVerdict != null && latestVerdict.requestsChanges()) {
            Duration remaining = clampNonNegative(Duration.between(Instant.now(), slaDeadline));
            prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.CHANGES_REQUESTED, remaining);
            notifications.add(new PendingNotification(
                    detectedPr.provider(),
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
                    detectedPr.provider(),
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.APPROVED,
                    sla,
                    slaDeadline,
                    teamLabel));
        } else {
            notifications.add(new PendingNotification(
                    detectedPr.provider(),
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.TRACKED,
                    sla,
                    slaDeadline,
                    teamLabel));
        }
        return PerPrResult.TRACKED;
    }

    private PerPrResult processNoSlaOpenPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            PrTrackingProps.Repository repoConfig,
            PrMetadata prMetadata,
            Map<String, Optional<Set<String>>> teamReviewerCache,
            List<PendingNotification> notifications) {

        if (!matchesPathFilter(
                repoConfig.paths(), detectedPr.provider(), detectedPr.repositoryName(), detectedPr.pullNumber())) {
            log.atDebug()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log("PR {}#{} does not match configured paths for no-SLA repo, skipping");
            return PerPrResult.SKIPPED;
        }

        TicketId ticketId = checkNotNull(ticket.id());
        PrTrackingRecord tracking = prTrackingRepository.insertIfAbsent(new NewPrTracking(
                ticketId.id(),
                detectedPr.provider(),
                detectedPr.repositoryName(),
                detectedPr.pullNumber(),
                prMetadata.createdAt(),
                null,
                repoConfig.owningTeam(),
                canAutoCloseTicket));
        if (tracking == null) {
            log.atInfo()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .addArgument(ticketId::id)
                    .log("PR {}#{} was already tracked for ticket {}, skipping");
            return PerPrResult.SKIPPED;
        }

        log.atInfo()
                .addArgument(detectedPr::repositoryName)
                .addArgument(detectedPr::pullNumber)
                .addArgument(ticketId::id)
                .log("PR {}#{} tracking record created for ticket {} (no-SLA repo)");

        addReaction(prTrackingProps.prEmoji(), ticket.queryTs(), ticket.channelId());
        String teamLabel = resolveTeamLabel(repoConfig.owningTeam());

        // Mirror the SLA branch: inspect reviews already fetched with the PR so that a no-SLA PR
        // detected while already in CHANGES_REQUESTED or APPROVED state transitions correctly on
        // first sight, instead of sitting in OPEN until the poller notices and posts a duplicate.
        List<Review> teamReviews =
                teamReviewFilter.filterToOwningTeam(prMetadata.reviews(), prMetadata, repoConfig, teamReviewerCache);
        Review latestVerdict = teamReviewFilter.findLatestActionableReview(teamReviews);

        if (latestVerdict != null && latestVerdict.requestsChanges()) {
            prTrackingRepository.updateStatus(tracking.id(), PrTrackingStatus.CHANGES_REQUESTED, null, null);
            notifications.add(new PendingNotification(
                    detectedPr.provider(),
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.CHANGES_REQUESTED,
                    null,
                    null,
                    teamLabel));
        } else if (latestVerdict != null && latestVerdict.isApproved()) {
            prTrackingRepository.updateStatus(tracking.id(), PrTrackingStatus.APPROVED, null, null);
            notifications.add(new PendingNotification(
                    detectedPr.provider(),
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.APPROVED,
                    null,
                    null,
                    teamLabel));
        } else {
            notifications.add(new PendingNotification(
                    detectedPr.provider(),
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.NO_SLA_TRACKED,
                    null,
                    null,
                    teamLabel));
        }

        return PerPrResult.TRACKED;
    }

    private boolean matchesPathFilter(List<String> paths, Provider provider, String repositoryName, int pullNumber) {

        if (paths.isEmpty()) {
            return true;
        }

        try {
            List<String> prFiles = prSourceClients
                    .forProvider(provider)
                    .listChangedFiles(new RepoCoord(provider, repositoryName), pullNumber);

            for (String pattern : paths) {
                for (String prFile : prFiles) {
                    if (PATH_MATCHER.match(pattern, prFile)) {
                        return true;
                    }
                }
            }
        } catch (PrSourceException e) {
            log.atError()
                    .addArgument(repositoryName)
                    .addArgument(pullNumber)
                    .addArgument(e::getMessage)
                    .log("Could not list files for {}#{} during path filter check, skipping: {}");
        }
        return false;
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

    private void postNotificationsAndEscalations(
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations,
            MessageTs queryTs,
            String channelId) {
        Map<String, List<PendingNotification>> notifsByRepo = new LinkedHashMap<>();
        for (PendingNotification pendingNotification : notifications) {
            notifsByRepo
                    .computeIfAbsent(pendingNotification.repo(), k -> new ArrayList<>())
                    .add(pendingNotification);
        }

        Map<String, List<PendingEscalation>> escalationsByRepo = new LinkedHashMap<>();
        for (PendingEscalation e : pendingEscalations) {
            escalationsByRepo
                    .computeIfAbsent(e.tracking().repo(), k -> new ArrayList<>())
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
            } catch (Exception e) {
                log.atError()
                        .setCause(e)
                        .addArgument(() -> repo)
                        .log("Failed to post notifications for repo {}, continuing with next repo");
            }

            for (PendingEscalation esc : escalationsByRepo.getOrDefault(repo, List.of())) {
                try {
                    escalateImmediately(
                            esc.tracking(), esc.ticket(), esc.tracking().owningTeam());
                } catch (Exception e) {
                    log.atError()
                            .setCause(e)
                            .addArgument(esc.tracking()::repo)
                            .addArgument(esc.tracking()::prNumber)
                            .log("Failed to escalate PR {}#{}, continuing");
                }
            }
        }
    }

    private void postSingleNotification(PendingNotification pendingNotification, MessageTs queryTs, String channelId) {
        MessageEvent event =
                switch (pendingNotification.type()) {
                    case TRACKED, NO_SLA_TRACKED -> MessageEvent.DETECTED;
                    case ESCALATED -> MessageEvent.ESCALATED;
                    case APPROVED -> MessageEvent.APPROVED;
                    case CHANGES_REQUESTED -> MessageEvent.CHANGES_REQUESTED;
                };
        PrMessageContext ctx = new PrMessageContext(
                pendingNotification.provider(),
                pendingNotification.repo(),
                pendingNotification.prNumber(),
                checkNotNull(pendingNotification.teamLabel()),
                pendingNotification.sla(),
                pendingNotification.slaDeadline());
        String override = messageRenderer.render(pendingNotification.repo(), event, ctx);
        Provider p = pendingNotification.provider();
        String noun = PrTerminology.noun(p);
        String sep = PrTerminology.separator(p);
        String longForm = PrTerminology.longForm(p);
        String plural = PrTerminology.plural(p);
        String text = override != null
                ? override
                : switch (pendingNotification.type()) {
                    case TRACKED ->
                        "%s submitted to `%s` are expected to be reviewed within %s. You don't have to ping us for reviews, but I'll keep an eye on this one. If <%s|%s %s%d> hasn't been reviewed by %s, I'll automatically escalate it to the owning team (%s)."
                                .formatted(
                                        longForm,
                                        pendingNotification.repo(),
                                        formatDuration(checkNotNull(pendingNotification.sla())),
                                        prUrl(pendingNotification.repo(), pendingNotification.prNumber()),
                                        noun,
                                        sep,
                                        pendingNotification.prNumber(),
                                        DEADLINE_FMT.format(checkNotNull(pendingNotification.slaDeadline())),
                                        checkNotNull(pendingNotification.teamLabel()));
                    case NO_SLA_TRACKED ->
                        "%s to %s have no automated SLAs, they are monitored by %s team. I'll still keep an eye on this one and let you know when it moves."
                                .formatted(
                                        plural,
                                        pendingNotification.repo(),
                                        checkNotNull(pendingNotification.teamLabel()));
                    case CHANGES_REQUESTED ->
                        "<%s|%s %s%d> for `%s` has been reviewed and changes have been requested. :eyes:"
                                .formatted(
                                        prUrl(pendingNotification.repo(), pendingNotification.prNumber()),
                                        noun,
                                        sep,
                                        pendingNotification.prNumber(),
                                        pendingNotification.repo());
                    case APPROVED ->
                        "<%s|%s %s%d> for `%s` has been approved and is ready to merge. :white_check_mark:"
                                .formatted(
                                        prUrl(pendingNotification.repo(), pendingNotification.prNumber()),
                                        noun,
                                        sep,
                                        pendingNotification.prNumber(),
                                        pendingNotification.repo());
                    case ESCALATED ->
                        formatEscalatedText(
                                p,
                                pendingNotification.repo(),
                                pendingNotification.prNumber(),
                                checkNotNull(pendingNotification.sla()));
                };
        postText(
                text,
                pendingNotification.repo(),
                pendingNotification.prNumber(),
                pendingNotification.type(),
                queryTs,
                channelId);
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

            // When a custom message is configured, post one message per PR so each has its own context.
            MessageEvent event =
                    switch (type) {
                        case TRACKED, NO_SLA_TRACKED -> MessageEvent.DETECTED;
                        case ESCALATED -> MessageEvent.ESCALATED;
                        case APPROVED -> MessageEvent.APPROVED;
                        case CHANGES_REQUESTED -> MessageEvent.CHANGES_REQUESTED;
                    };
            if (messageRenderer.hasOverride(repo, event)) {
                group.forEach(n -> postSingleNotification(n, queryTs, channelId));
                continue;
            }

            // Provider is consistent within a (repo, type) group: PrTrackingProps disallows the same
            // repo name across providers and group entries share repo. Take it from the first.
            Provider groupProvider = group.getFirst().provider();
            String groupPlural = PrTerminology.plural(groupProvider);
            String groupSep = PrTerminology.separator(groupProvider);
            String prList = group.stream()
                    .map(n -> "<%s|%s%d>".formatted(prUrl(n.repo(), n.prNumber()), groupSep, n.prNumber()))
                    .collect(Collectors.joining(", "));

            String text =
                    switch (type) {
                        case TRACKED -> formatTrackedGroup(repo, group, prList, groupProvider);
                        case NO_SLA_TRACKED ->
                            "%s %s have no automated SLAs, they are monitored by %s team(s). I'll still keep an eye on them and let you know when they move."
                                    .formatted(groupPlural, prList, teams(group));
                        case CHANGES_REQUESTED ->
                            "%s %s for `%s` have been reviewed and changes have been requested. :eyes:"
                                    .formatted(groupPlural, prList, repo);
                        case APPROVED ->
                            "%s %s for `%s` have been approved and are ready to merge. :white_check_mark:"
                                    .formatted(groupPlural, prList, repo);
                        case ESCALATED ->
                            "%s %s for `%s` are expected to be reviewed within %s. They have exceeded that timeframe — escalating. :rocket:"
                                    .formatted(
                                            groupPlural,
                                            prList,
                                            repo,
                                            formatDuration(checkNotNull(
                                                    group.getFirst().sla())));
                    };
            postText(text, repo, 0, type, queryTs, channelId);
        }
    }

    private String teams(List<PendingNotification> group) {
        List<String> labels =
                group.stream().map(PendingNotification::teamLabel).distinct().toList();
        if (labels.size() < group.size()) {
            log.atDebug()
                    .addArgument(group.size())
                    .addArgument(labels.size())
                    .addArgument(labels)
                    .log("team label dedup: {} notification(s) collapsed to {} unique label(s): {}");
        }
        return String.join(", ", labels);
    }

    private String formatTrackedGroup(String repo, List<PendingNotification> group, String prList, Provider provider) {
        String teamLabel = group.getFirst().teamLabel();
        boolean sameSla =
                group.stream().map(PendingNotification::slaDeadline).distinct().count() == 1;
        String plural = PrTerminology.plural(provider);
        String longForm = PrTerminology.longForm(provider);
        String separator = PrTerminology.separator(provider);

        if (sameSla) {
            Instant deadline = checkNotNull(group.getFirst().slaDeadline());
            Duration sla = checkNotNull(group.getFirst().sla());
            return ("I'm tracking %s %s for `%s`. %s are expected to be reviewed within %s. "
                            + "You don't have to ping for reviews — I'll keep an eye on these. "
                            + "If not reviewed by %s, I'll automatically escalate to the owning team (%s).")
                    .formatted(
                            plural,
                            prList,
                            repo,
                            longForm,
                            formatDuration(sla),
                            DEADLINE_FMT.format(deadline),
                            teamLabel);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("I'm tracking %s for `%s`. You don't have to ping for reviews — I'll keep an eye on these:\n"
                    .formatted(plural, repo));
            for (PendingNotification n : group) {
                sb.append("- <%s|%s%d> — review by %s\n"
                        .formatted(
                                prUrl(n.repo(), n.prNumber()),
                                separator,
                                n.prNumber(),
                                DEADLINE_FMT.format(checkNotNull(n.slaDeadline()))));
            }
            sb.append("If not reviewed by their deadline, I'll escalate to the owning team (%s).".formatted(teamLabel));
            return sb.toString();
        }
    }

    private String prUrl(String repo, int prNumber) {
        return prUrlResolver.publicUrlFor(repo, prNumber);
    }

    private String formatEscalatedText(Provider provider, String repo, int prNumber, Duration sla) {
        return "%s submitted to `%s` are expected to be reviewed within %s. It looks like <%s|%s %s%d> has exceeded that timeframe."
                .formatted(
                        PrTerminology.longForm(provider),
                        repo,
                        formatDuration(sla),
                        prUrl(repo, prNumber),
                        PrTerminology.noun(provider),
                        PrTerminology.separator(provider),
                        prNumber);
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
                .addArgument(tracking::repo)
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
                    .addArgument(tracking::repo)
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
