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
import com.coreeng.supportbot.prtracking.source.CodeOwnerRef;
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
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.coreeng.supportbot.ticket.TicketTeamSuggestionsService;
import com.coreeng.supportbot.ticket.TicketTeamsSuggestion;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.model.User;
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
    private final PlatformTeamsService platformTeamsService;
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;
    private final SlaLookup slaLookup;
    private final PrMessageRenderer messageRenderer;
    private final PrUrlResolver prUrlResolver;

    // Whether any configured repo has a non-empty exclude-author-teams list. Fixed at config-bind
    // time, so it is computed once (lazily) rather than re-streamed on every message.
    private volatile @Nullable Boolean anyRepoExcludesAuthorsCache;

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
        Optional<Set<String>> posterTeamCodes =
                anyRepoExcludesAuthors() ? resolvePosterTeamCodes(event.userId()) : Optional.of(Set.of());
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
            Optional<PrTrackingProps.Repository> repoConfig = repoConfigFor(pr);
            PerPrResult result;
            try {
                result = processPr(
                        pr,
                        ticket,
                        canAutoCloseTicket,
                        prMetadata,
                        repoConfig,
                        posterTeamCodes,
                        teamReviewerCache,
                        notifications,
                        pendingEscalations);
            } catch (Exception e) {
                log.atError()
                        .setCause(e)
                        .addArgument(pr::repositoryName)
                        .addArgument(pr::pullNumber)
                        .addArgument(ticketId::id)
                        .log("Failed to process PR {}#{} for ticket {}, skipping");
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
        Optional<Set<String>> posterTeamCodes =
                anyRepoExcludesAuthors() ? resolvePosterTeamCodes(event.userId()) : Optional.of(Set.of());
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

                // Must run before the ticketSupplier below, or a PR posted by an excluded author would
                // still auto-create a ticket. processPr re-checks against the same resolved poster teams.
                Optional<PrTrackingProps.Repository> repoConfig = repoConfigFor(pr);
                if (repoConfig.isPresent() && authorExcluded(pr, repoConfig.get(), posterTeamCodes)) {
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

                PerPrResult result = processPr(
                        pr,
                        ticket,
                        true,
                        prMetadata,
                        repoConfig,
                        posterTeamCodes,
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
                TicketId ticketIdForLog = ticketId;
                log.atError()
                        .setCause(e)
                        .addArgument(pr::repositoryName)
                        .addArgument(pr::pullNumber)
                        .addArgument(() -> ticketIdForLog == null ? "none" : ticketIdForLog.id())
                        .log("Failed to process PR {}#{} for ticket {}, skipping");
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

    private Optional<PrTrackingProps.Repository> repoConfigFor(DetectedPr detectedPr) {
        return prTrackingProps.repositories().stream()
                .filter(r -> r.name().equals(detectedPr.repositoryName()))
                .findFirst();
    }

    private PerPrResult processPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            PrMetadata prMetadata,
            Optional<PrTrackingProps.Repository> repoConfig,
            Optional<Set<String>> posterTeamCodes,
            Map<String, Optional<Set<String>>> teamReviewerCache,
            List<PendingNotification> notifications,
            List<PendingEscalation> pendingEscalations) {

        if (repoConfig.isPresent()) {
            if (authorExcluded(detectedPr, repoConfig.get(), posterTeamCodes)) {
                return PerPrResult.SKIPPED;
            }
            // Code-owner repos: held in OPEN with no review deadline by default, unless the pending code
            // owners ARE the repo's maintaining team (see processCodeownerOpenPr). Checked before the SLA
            // branches since it applies whether or not the repo has an SLA configured.
            if (repoConfig.get().requiresCodeowners()) {
                return processCodeownerOpenPr(
                        detectedPr, ticket, canAutoCloseTicket, repoConfig.get(), prMetadata, teamReviewerCache);
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
     * Author admission gate (#285): skips a PR/MR when the Slack user who posted it belongs to one of
     * the repo's {@code exclude-author-teams} (any-of). Membership is resolved through the bot's
     * platform teams (Slack/IdP-backed, keyed by email) — not the VCS provider. Fails open (tracks
     * anyway) when no deny-list is configured or the poster's team membership cannot be determined.
     */
    private boolean authorExcluded(
            DetectedPr detectedPr, PrTrackingProps.Repository repoConfig, Optional<Set<String>> posterTeamCodes) {
        List<String> excludedTeams = repoConfig.excludeAuthorTeams();
        if (excludedTeams.isEmpty()) {
            return false;
        }
        if (posterTeamCodes.isEmpty()) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log(
                            "PR {}#{} poster team membership could not be resolved but exclude-author-teams is configured — tracking anyway");
            return false;
        }
        Set<String> posterCodes = posterTeamCodes.get();
        for (String team : excludedTeams) {
            if (posterCodes.contains(team)) {
                log.atInfo()
                        .addArgument(detectedPr::repositoryName)
                        .addArgument(detectedPr::pullNumber)
                        .addArgument(() -> team)
                        .log("PR {}#{} poster is in excluded-author-team {} — skipping tracking");
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the Slack poster's platform-team codes for the admission gate. Returns an empty
     * {@link Optional} (fail open) when the poster has no resolvable Slack email or the lookup throws —
     * distinct from {@code Optional.of(emptySet())}, which means "resolved, but a member of no team".
     */
    private Optional<Set<String>> resolvePosterTeamCodes(String slackUserId) {
        try {
            User user = slackClient.getUserById(SlackId.user(slackUserId));
            String email = user.getProfile().getEmail();
            if (email == null || email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(platformTeamsService.listTeamsByUserEmail(email).stream()
                    .map(PlatformTeam::code)
                    .collect(Collectors.toSet()));
        } catch (RuntimeException e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(() -> slackUserId)
                    .log("Failed to resolve Slack poster {} team membership for author admission — tracking anyway");
            return Optional.empty();
        }
    }

    private boolean anyRepoExcludesAuthors() {
        Boolean cached = anyRepoExcludesAuthorsCache;
        if (cached == null) {
            cached = prTrackingProps.repositories().stream()
                    .anyMatch(r -> !r.excludeAuthorTeams().isEmpty());
            anyRepoExcludesAuthorsCache = cached;
        }
        return cached;
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
                postBreachAndEscalate(
                        detectedPr, ticket, tracking, repoConfig.owningTeam(), teamLabel, sla, slaDeadline);
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

    /**
     * Detection for requires-codeowners repos. By default: no review deadline, "chase the code owner"
     * message, never review-escalates — unchanged from before this class had any code-owner awareness.
     * On GitHub, {@link #excludingOwningTeam} drops the repo's own maintaining team from the chase list,
     * so a pending list mixing the maintaining team with a genuinely external reviewer names only the
     * external one.
     *
     * <p>One carve-out (an exception to that default): if the pending code owners ARE the repo's own
     * maintaining team ({@link #pendingCodeOwnersAreMaintainingTeam}), chasing them makes no sense — the
     * message never names them as something to chase, regardless of {@code sla}. The repo's configured
     * override always wins first if present (same precedence as everywhere else in this file); failing
     * that, {@code sla} configured gets a real review deadline and the normal tracked-with-a-deadline
     * message and can review-escalate on breach like any repo, while no {@code sla} gets the standard
     * no-SLA-tracked message. Everything else (a genuinely external pending code owner) keeps the
     * default chase message even if an override is configured — the override applies to the
     * normal/no-codeowner flow, not to a real chase.
     *
     * <p>A copy-only variation (no effect on the deadline or escalation): a draft PR with an empty pending
     * list and a confirmed-unsatisfied gate that isn't changes-requested gets {@link
     * #formatCodeownerDraftPendingText} instead of the default chase copy.
     *
     * <p>Path filtering still applies with no {@code sla} block: like any no-SLA repo, only PRs touching
     * the configured {@code paths} are tracked.
     */
    private PerPrResult processCodeownerOpenPr(
            DetectedPr detectedPr,
            Ticket ticket,
            boolean canAutoCloseTicket,
            PrTrackingProps.Repository repoConfig,
            PrMetadata prMetadata,
            Map<String, Optional<Set<String>>> teamReviewerCache) {

        if (repoConfig.hasNoSla()
                && !matchesPathFilter(
                        repoConfig.paths(),
                        detectedPr.provider(),
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber())) {
            log.atDebug()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log("PR {}#{} does not match configured paths for no-SLA requires-codeowners repo, skipping");
            return PerPrResult.SKIPPED;
        }

        // Only give this PR a real review deadline if the pending code owners are the repo's own
        // maintaining team. Otherwise slaDeadline stays null, same as before this feature existed.
        boolean codeOwnerIsMaintainingTeam = pendingCodeOwnersAreMaintainingTeam(
                detectedPr, repoConfig, prMetadata.codeOwnerReviewers(), teamReviewerCache);
        ReviewSlaResolution reviewSla = codeOwnerIsMaintainingTeam
                ? codeownerReviewSlaDeadline(detectedPr, repoConfig, prMetadata)
                : ReviewSlaResolution.NOT_APPLICABLE;
        Instant slaDeadline = reviewSla.deadline();

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

        // Mark the code-owner-request flag at insert time too, not just from the poller: the pending
        // window can close (approved then dismissed by a push) before the first poll ever runs — overnight
        // or over a weekend under the default business-hours cron — and it never reopens (GitHub does not
        // restore a dismissed reviewer to reviewRequests). Purely observability today (see
        // PrTrackingRecord#codeownerReviewRequested), but the audit trail should reflect reality even for
        // a PR that closes before it's ever polled.
        if (!prMetadata.codeOwnerReviewers().isEmpty()) {
            tracking = prTrackingRepository.markCodeownerReviewRequested(tracking.id());
        }

        log.atInfo()
                .addArgument(detectedPr::repositoryName)
                .addArgument(detectedPr::pullNumber)
                .addArgument(ticketId::id)
                .addArgument(() -> slaDeadline == null ? "none" : slaDeadline)
                .log("PR {}#{} tracking record created for ticket {} (codeowner repo, review deadline: {})");

        addReaction(prTrackingProps.prEmoji(), ticket.queryTs(), ticket.channelId());

        // Skip the DETECTED message if the gate's already satisfied (it would be factually wrong to say
        // "waiting on code owners") — the record and reaction still land, and the poller catches up next
        // cycle with the accurate "awaiting merge" message.
        if (!Boolean.TRUE.equals(prMetadata.codeOwnersApproved())) {
            if (reviewSla.lookupFailed()) {
                // We confirmed these code owners ARE the maintaining team, but the SLA lookup that would
                // give the message a real deadline just failed. Neither the chase copy (telling the tenant
                // to chase their own team) nor the deadline copy (no deadline) is accurate — post nothing.
                log.atInfo()
                        .addArgument(detectedPr::repositoryName)
                        .addArgument(detectedPr::pullNumber)
                        .log("PR {}#{}: maintaining-team carve-out confirmed but SLA lookup failed, skipping message");
            } else if (slaDeadline != null && Instant.now().isAfter(slaDeadline)) {
                // Already overdue at detection (e.g. an old PR just linked, or a short SLA like a few
                // minutes) — mirrors processOpenPr's immediate-breach handling instead of posting a
                // "tracked" message with a deadline that's already in the past.
                handleCodeownerAlreadyOverdue(detectedPr, ticket, tracking, repoConfig, prMetadata, slaDeadline);
            } else {
                String text;
                if (codeOwnerIsMaintainingTeam) {
                    // See the maintaining-team carve-out paragraph in this method's class-level Javadoc.
                    Duration reviewSlaDuration =
                            slaDeadline != null ? Duration.between(prMetadata.createdAt(), slaDeadline) : null;
                    PrMessageContext ctx = new PrMessageContext(
                            detectedPr.provider(),
                            detectedPr.repositoryName(),
                            detectedPr.pullNumber(),
                            resolveTeamLabel(repoConfig.owningTeam()),
                            reviewSlaDuration,
                            slaDeadline);
                    String override = messageRenderer.render(detectedPr.repositoryName(), MessageEvent.DETECTED, ctx);
                    if (override != null) {
                        text = override;
                    } else if (slaDeadline != null) {
                        text = formatTrackedText(
                                detectedPr.provider(),
                                detectedPr.repositoryName(),
                                detectedPr.pullNumber(),
                                checkNotNull(ctx.sla()),
                                checkNotNull(ctx.slaDeadline()),
                                ctx.owningTeam());
                    } else {
                        text = formatNoSlaTrackedText(
                                detectedPr.provider(),
                                detectedPr.repositoryName(),
                                resolveTeamLabel(repoConfig.owningTeam()));
                    }
                } else if (prMetadata.isDraft()
                        && prMetadata.codeOwnerReviewers().isEmpty()
                        && Boolean.FALSE.equals(prMetadata.codeOwnersApproved())
                        && !prMetadata.codeownerChangesRequested()) {
                    // An empty pending list on a draft means the generic "needs code-owner approval" copy
                    // would imply someone was asked when nobody may have been. codeOwnersApproved==FALSE
                    // (rather than just an empty list) keeps this off the unknown case, where the gate
                    // couldn't be read at all and the value is null.
                    text = formatCodeownerDraftPendingText(
                            detectedPr.provider(), detectedPr.repositoryName(), detectedPr.pullNumber());
                } else {
                    text = formatCodeownerDetectedText(
                            detectedPr.provider(),
                            detectedPr.repositoryName(),
                            detectedPr.pullNumber(),
                            excludingOwningTeam(
                                    detectedPr, repoConfig, prMetadata.codeOwnerReviewers(), teamReviewerCache));
                }
                postText(
                        text,
                        detectedPr.repositoryName(),
                        detectedPr.pullNumber(),
                        NotificationType.TRACKED,
                        ticket.queryTs(),
                        ticket.channelId());
            }
        }
        return PerPrResult.TRACKED;
    }

    /**
     * Mirrors {@link #processOpenPr}'s immediate-breach handling for the maintaining-team carve-out: a
     * changes-requested verdict still wins over escalating (same priority as everywhere else in the FSM),
     * otherwise this escalates right now instead of waiting for the next poll.
     */
    private void handleCodeownerAlreadyOverdue(
            DetectedPr detectedPr,
            Ticket ticket,
            PrTrackingRecord tracking,
            PrTrackingProps.Repository repoConfig,
            PrMetadata prMetadata,
            Instant slaDeadline) {
        Duration sla = Duration.between(prMetadata.createdAt(), slaDeadline);
        if (prMetadata.codeownerChangesRequested()) {
            prTrackingRepository.pauseSla(tracking.id(), PrTrackingStatus.CHANGES_REQUESTED, Duration.ZERO);
            postText(
                    formatChangesRequestedText(
                            detectedPr.provider(), detectedPr.repositoryName(), detectedPr.pullNumber()),
                    detectedPr.repositoryName(),
                    detectedPr.pullNumber(),
                    NotificationType.CHANGES_REQUESTED,
                    ticket.queryTs(),
                    ticket.channelId());
            return;
        }
        postBreachAndEscalate(
                detectedPr,
                ticket,
                tracking,
                repoConfig.owningTeam(),
                resolveTeamLabel(repoConfig.owningTeam()),
                sla,
                slaDeadline);
    }

    /** Shared by {@link #processOpenPr} and {@link #handleCodeownerAlreadyOverdue}: post the breach notification,
     * then create the escalation. */
    private void postBreachAndEscalate(
            DetectedPr detectedPr,
            Ticket ticket,
            PrTrackingRecord tracking,
            String owningTeam,
            String teamLabel,
            Duration sla,
            Instant slaDeadline) {
        PrMessageContext breachCtx = new PrMessageContext(
                detectedPr.provider(),
                detectedPr.repositoryName(),
                detectedPr.pullNumber(),
                teamLabel,
                sla,
                slaDeadline);
        String override = messageRenderer.render(detectedPr.repositoryName(), MessageEvent.ESCALATED, breachCtx);
        String breachText = override != null
                ? override
                : formatEscalatedText(detectedPr.provider(), detectedPr.repositoryName(), detectedPr.pullNumber(), sla);
        postText(
                breachText,
                detectedPr.repositoryName(),
                detectedPr.pullNumber(),
                NotificationType.ESCALATED,
                ticket.queryTs(),
                ticket.channelId());
        escalateImmediately(tracking, ticket, owningTeam);
    }

    /**
     * {@code deadline} is null for several reasons (no SLA configured, nothing resolved, or the lookup
     * failed) — {@code lookupFailed} isolates just the last one, since that's the only case the caller
     * needs to treat differently (skip the message instead of showing the default chase copy).
     */
    private record ReviewSlaResolution(@Nullable Instant deadline, boolean lookupFailed) {
        static final ReviewSlaResolution NOT_APPLICABLE = new ReviewSlaResolution(null, false);
    }

    /**
     * Review deadline for the maintaining-team carve-out — only called once that's already confirmed.
     * Resolves the SLA via the same {@link SlaLookup#getSla} call as {@link #processOpenPr}, but with
     * different failure handling: {@link #processOpenPr} skips tracking the PR outright on a lookup
     * failure, whereas here a failure degrades to "no deadline" and the PR is still tracked (see {@code
     * lookupFailed}); this is a one-shot attempt, not retried later.
     */
    private ReviewSlaResolution codeownerReviewSlaDeadline(
            DetectedPr detectedPr, PrTrackingProps.Repository repoConfig, PrMetadata prMetadata) {
        if (repoConfig.hasNoSla()) {
            return ReviewSlaResolution.NOT_APPLICABLE;
        }
        try {
            Duration sla = slaLookup.getSla(
                    repoConfig,
                    new RepoCoord(detectedPr.provider(), detectedPr.repositoryName()),
                    detectedPr.pullNumber());
            Instant deadline = sla != null ? prMetadata.createdAt().plus(sla) : null;
            return new ReviewSlaResolution(deadline, false);
        } catch (PrSourceException e) {
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .addArgument(e::getMessage)
                    .log(
                            "Failed to look up review-phase SLA for codeowner repo {}#{}, tracking without a deadline: {}");
            return new ReviewSlaResolution(null, true);
        }
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
        String text = override != null
                ? override
                : switch (pendingNotification.type()) {
                    case TRACKED ->
                        formatTrackedText(
                                p,
                                pendingNotification.repo(),
                                pendingNotification.prNumber(),
                                checkNotNull(pendingNotification.sla()),
                                checkNotNull(pendingNotification.slaDeadline()),
                                checkNotNull(pendingNotification.teamLabel()));
                    case NO_SLA_TRACKED ->
                        formatNoSlaTrackedText(
                                p, pendingNotification.repo(), checkNotNull(pendingNotification.teamLabel()));
                    case CHANGES_REQUESTED ->
                        formatChangesRequestedText(p, pendingNotification.repo(), pendingNotification.prNumber());
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

            // When a custom message is configured, post it once for the whole repo group rather
            // than once per PR. The override text is repo/event-level (not PR-specific), so N PRs
            // in the same repo should yield a single message — mirroring the single grouped message
            // the default path produces below. Rendered with the first PR's context.
            MessageEvent event =
                    switch (type) {
                        case TRACKED, NO_SLA_TRACKED -> MessageEvent.DETECTED;
                        case ESCALATED -> MessageEvent.ESCALATED;
                        case APPROVED -> MessageEvent.APPROVED;
                        case CHANGES_REQUESTED -> MessageEvent.CHANGES_REQUESTED;
                    };
            if (messageRenderer.hasOverride(repo, event)) {
                // We reach here only when the group has >1 PR. A custom override is free-form text
                // rendered with a single PR's context, so the other PRs can't be named in it — log
                // them so the collapse is observable (e.g. if a repo uses a PR-specific override).
                log.atDebug()
                        .addArgument(repo)
                        .addArgument(() -> group.stream()
                                .map(n -> String.valueOf(n.prNumber()))
                                .collect(Collectors.joining(", ")))
                        .log(
                                "Custom override for repo {} posted once for PRs {} (rendered with the first PR's context)");
                postSingleNotification(group.getFirst(), queryTs, channelId);
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

    /**
     * The "tracked with a review deadline" message a normal SLA'd repo gets on detection. Shared between
     * {@link #postSingleNotification}'s {@code TRACKED} case and {@link #processCodeownerOpenPr}'s
     * maintainer-overlap case (see {@link #pendingCodeOwnersAreMaintainingTeam}) — the latter needs the
     * same wording, just reached from a different code-owner-specific code path.
     */
    private String formatTrackedText(
            Provider provider, String repo, int prNumber, Duration sla, Instant slaDeadline, String teamLabel) {
        return "%s submitted to `%s` are expected to be reviewed within %s. You don't have to ping us for reviews, but I'll keep an eye on this one. If <%s|%s %s%d> hasn't been reviewed by %s, I'll automatically escalate it to the owning team (%s)."
                .formatted(
                        PrTerminology.longForm(provider),
                        repo,
                        formatDuration(sla),
                        prUrl(repo, prNumber),
                        PrTerminology.noun(provider),
                        PrTerminology.separator(provider),
                        prNumber,
                        DEADLINE_FMT.format(slaDeadline),
                        teamLabel);
    }

    /**
     * Shared between {@link #postSingleNotification}'s {@code CHANGES_REQUESTED} case and the codeowner
     * already-overdue-at-detection path — same wording, different callers.
     */
    private String formatChangesRequestedText(Provider provider, String repo, int prNumber) {
        return "<%s|%s %s%d> for `%s` has been reviewed and changes have been requested. :eyes:"
                .formatted(
                        prUrl(repo, prNumber),
                        PrTerminology.noun(provider),
                        PrTerminology.separator(provider),
                        prNumber,
                        repo);
    }

    private String formatNoSlaTrackedText(Provider provider, String repo, String teamLabel) {
        return "%s to %s have no automated SLAs, they are monitored by %s team. I'll still keep an eye on this one and let you know when it moves."
                .formatted(PrTerminology.plural(provider), repo, teamLabel);
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

    private String formatCodeownerDetectedText(
            Provider provider, String repo, int prNumber, List<CodeOwnerRef> codeOwners) {
        String prRef = "<%s|%s %s%d>"
                .formatted(
                        prUrl(repo, prNumber),
                        PrTerminology.noun(provider),
                        PrTerminology.separator(provider),
                        prNumber);
        if (codeOwners.isEmpty()) {
            return "%s in `%s` needs code-owner approval before the owning team can merge it. I'll let you know once the code owners have approved."
                    .formatted(prRef, repo);
        }
        String owners =
                codeOwners.stream().map(PrDetectionService::renderCodeOwner).collect(Collectors.joining(", "));
        return "%s in `%s` needs code-owner approval before the owning team can merge it. You may need to chase: %s. I'll let you know once they've approved."
                .formatted(prRef, repo, owners);
    }

    /**
     * Draft counterpart to {@link #formatCodeownerDetectedText}'s empty-list branch, which would imply
     * someone had already been asked. States only that the PR is a draft — an empty pending list has
     * several indistinguishable causes (never auto-requested, a review already submitted, an approval
     * dismissed by a push), so the copy deliberately attributes none of them.
     */
    private String formatCodeownerDraftPendingText(Provider provider, String repo, int prNumber) {
        String prRef = "<%s|%s %s%d>"
                .formatted(
                        prUrl(repo, prNumber),
                        PrTerminology.noun(provider),
                        PrTerminology.separator(provider),
                        prNumber);
        return "%s in `%s` is still a draft, so it's not ready for code-owner review yet. I'll let you know once the code owners have approved."
                .formatted(prRef, repo);
    }

    /**
     * True when every pending code owner on this PR is a member of the repo's configured maintaining team
     * ({@code github-team-slug} / {@code gitlab-group-path}). A one-shot check at detection time — never
     * re-evaluated later.
     *
     * <p>Defaults to {@code false} whenever it can't be confirmed for sure: nothing pending, no team
     * configured, or membership can't be resolved. That default doesn't always mean the chase copy is
     * used verbatim — an empty pending list on a draft PR can instead get {@link
     * #formatCodeownerDraftPendingText}, and even the default chase copy has the maintaining team
     * excluded from it by {@link #excludingOwningTeam}.
     */
    private boolean pendingCodeOwnersAreMaintainingTeam(
            DetectedPr detectedPr,
            PrTrackingProps.Repository repoConfig,
            List<CodeOwnerRef> pending,
            Map<String, Optional<Set<String>>> teamReviewerCache) {
        if (pending.isEmpty()) {
            return false;
        }
        RepoCoord coord = new RepoCoord(detectedPr.provider(), detectedPr.repositoryName());
        if (detectedPr.provider() == Provider.GITHUB) {
            String teamSlug = repoConfig.githubTeamSlug();
            if (teamSlug == null) {
                return false;
            }
            String expectedTeamDisplay = expectedGithubTeamDisplay(detectedPr, teamSlug);
            if (expectedTeamDisplay == null) {
                logUnparseableRepoName(
                        detectedPr, "can't confirm the maintaining-team carve-out, keeping the code-owner chase copy");
                return false;
            }
            for (CodeOwnerRef ref : pending) {
                if (ref.isTeam()) {
                    if (!ref.display().equalsIgnoreCase(expectedTeamDisplay)) {
                        return false;
                    }
                } else {
                    Set<String> members = teamReviewFilter.resolveTeamMembers(coord, teamSlug, teamReviewerCache);
                    if (members == null) {
                        logTeamResolutionFailed(detectedPr, teamSlug);
                        return false;
                    }
                    // GitHub logins are case-insensitive, so match the same way the team-ref branch above does.
                    if (members.stream().noneMatch(member -> member.equalsIgnoreCase(ref.display()))) {
                        return false;
                    }
                }
            }
            return true;
        }
        if (detectedPr.provider() == Provider.GITLAB) {
            String groupPath = repoConfig.gitlabGroupPath();
            if (groupPath == null) {
                return false;
            }
            Set<String> members = teamReviewFilter.resolveTeamMembers(coord, groupPath, teamReviewerCache);
            if (members == null) {
                logTeamResolutionFailed(detectedPr, groupPath);
                return false;
            }
            for (CodeOwnerRef ref : pending) {
                // GitLab usernames/paths are case-insensitive, so match the same way the GitHub branch
                // above does rather than relying on the API always returning canonical case.
                if (members.stream().noneMatch(member -> member.equalsIgnoreCase(ref.display()))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Drops the repo's own maintaining team ({@code github-team-slug}) — as a team ref, or as an
     * individual member of it — out of a pending code-owner list before it's rendered in the chase
     * message. {@link #pendingCodeOwnersAreMaintainingTeam} only suppresses the chase copy when the
     * maintaining team is the ENTIRE pending list; when the list is mixed (the maintaining team plus a
     * genuinely external reviewer), that carve-out doesn't engage and the chase message would otherwise
     * tell the owning team to chase itself. This never changes which repos get the carve-out or their SLA
     * deadline — it only trims what gets named in the chase text. Mirrors
     * {@link #pendingCodeOwnersAreMaintainingTeam}'s own per-entry matching (team-ref display, or
     * individual membership via {@link #teamReviewFilter}), so anything that method would count as "the
     * maintaining team" is excluded here too.
     */
    private List<CodeOwnerRef> excludingOwningTeam(
            DetectedPr detectedPr,
            PrTrackingProps.Repository repoConfig,
            List<CodeOwnerRef> pending,
            Map<String, Optional<Set<String>>> teamReviewerCache) {
        if (pending.isEmpty() || detectedPr.provider() != Provider.GITHUB) {
            return pending;
        }
        String teamSlug = repoConfig.githubTeamSlug();
        if (teamSlug == null) {
            return pending;
        }
        String expectedTeamDisplay = expectedGithubTeamDisplay(detectedPr, teamSlug);
        if (expectedTeamDisplay == null) {
            logUnparseableRepoName(
                    detectedPr, "leaving the chase list unfiltered, so it may name the repo's own maintaining team");
            return pending;
        }
        // Resolving members needs org Members:Read, which repos listing only teams in CODEOWNERS never
        // otherwise pay for, so only look it up when an individual ref actually has to be matched.
        Set<String> members = null;
        if (pending.stream().anyMatch(ref -> !ref.isTeam())) {
            RepoCoord coord = new RepoCoord(detectedPr.provider(), detectedPr.repositoryName());
            members = teamReviewFilter.resolveTeamMembers(coord, teamSlug, teamReviewerCache);
            if (members == null) {
                logTeamResolutionFailed(detectedPr, teamSlug);
            }
        }
        Set<String> resolvedMembers = members;
        List<CodeOwnerRef> filtered = pending.stream()
                .filter(ref -> {
                    if (ref.isTeam()) {
                        return !ref.display().equalsIgnoreCase(expectedTeamDisplay);
                    }
                    // resolvedMembers == null means resolution failed, same as the sibling carve-out
                    // check — fail safe by keeping the entry in the chase list rather than risking
                    // dropping a genuinely external reviewer we couldn't confirm is a team member.
                    return resolvedMembers == null
                            || resolvedMembers.stream().noneMatch(member -> member.equalsIgnoreCase(ref.display()));
                })
                .toList();
        if (filtered.isEmpty()) {
            // The carve-out runs first on this same list and would have suppressed the chase copy
            // entirely if every entry were the maintaining team, so this should be unreachable. Falling
            // back to the unfiltered list keeps a real chase list rather than silently naming nobody.
            log.atWarn()
                    .addArgument(detectedPr::repositoryName)
                    .addArgument(detectedPr::pullNumber)
                    .log("Owning-team filter emptied the chase list for {}#{}, falling back to the unfiltered list");
            return pending;
        }
        return filtered;
    }

    /**
     * The org-qualified display string ({@code org/team}) GitHub uses for a team ref, derived from the
     * repo's own {@code org/repo} name and the configured {@code teamSlug}. {@code null} when the repo
     * name has no parseable org prefix (it came from parsing whatever URL the user pasted, so "org/repo"
     * isn't guaranteed); callers log that themselves, since the consequence differs per caller.
     */
    private @Nullable String expectedGithubTeamDisplay(DetectedPr detectedPr, String teamSlug) {
        String repoName = detectedPr.repositoryName();
        int slash = repoName.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return repoName.substring(0, slash) + "/" + teamSlug;
    }

    /** Marks that an unparseable {@code org/repo} name, not a genuine mismatch, is why a check bailed. */
    private void logUnparseableRepoName(DetectedPr detectedPr, String consequence) {
        log.atWarn()
                .addArgument(detectedPr::repositoryName)
                .addArgument(detectedPr::pullNumber)
                .addArgument(() -> consequence)
                .log("Repo name {} for PR #{} has no org/repo separator — {}");
    }

    /** Marks that a lookup failure, not a genuine mismatch, is why the carve-out didn't engage. */
    private void logTeamResolutionFailed(DetectedPr detectedPr, String teamRef) {
        log.atWarn()
                .addArgument(() -> teamRef)
                .addArgument(detectedPr::repositoryName)
                .addArgument(detectedPr::pullNumber)
                .log(
                        "Could not resolve {} team membership for {}#{} — falling back to the code-owner chase copy instead of confirming the maintaining-team carve-out");
    }

    /**
     * Renders a code owner for the chase list: a linked GitHub login/team when a URL is known (plain
     * backticked text otherwise), with teams prefixed by a people marker and shown org-qualified
     * (e.g. {@code org/team}) so they're distinguishable from individual users.
     */
    private static String renderCodeOwner(CodeOwnerRef ref) {
        // U+1F465 BUSTS IN SILHOUETTE, rendered as a "busts" emoji in Slack; built from the code point so the
        // source stays ASCII.
        String marker = ref.isTeam() ? new String(Character.toChars(0x1F465)) + " " : "";
        String url = ref.url();
        String label =
                url != null && !url.isBlank() ? "<" + url + "|" + ref.display() + ">" : "`" + ref.display() + "`";
        return marker + label;
    }

    private void postText(
            String text, String repo, int prNumber, NotificationType type, MessageTs queryTs, String channelId) {
        try {
            slackClient.postMessage(new SlackPostMessageRequest(
                    SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
            log.atInfo().addArgument(() -> repo).addArgument(() -> type).log("Notification posted for {} ({})");
        } catch (Exception e) {
            // Broad catch is deliberate: this post is best-effort and must never block the escalation
            // that follows it in callers like postBreachAndEscalate — a non-SlackException failure here
            // (not just a Slack API error) shouldn't skip paging the owning team.
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
