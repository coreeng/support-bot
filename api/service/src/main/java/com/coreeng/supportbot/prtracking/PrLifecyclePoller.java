package com.coreeng.supportbot.prtracking;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.PrTrackingProps;
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
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls tracked PRs and advances their lifecycle. The FSM is expressed declaratively in {@link
 * PrLifecycle}: this class is the imperative shell — {@code observe()} snapshots the world, {@link
 * PrLifecycle#decide} picks the next state + ordered effects (pure), and {@code apply()} runs them.
 */
@Component
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PrLifecyclePoller {

    private final PrTrackingRepository prTrackingRepository;
    private final PrSourceClients prSourceClients;
    private final TeamReviewFilter teamReviewFilter;
    private final TicketRepository ticketRepository;
    private final TicketProcessingService ticketProcessingService;
    private final EscalationProcessingService escalationProcessingService;
    private final TicketSlackService ticketSlackService;
    private final SlackClient slackClient;
    private final PrTrackingProps prTrackingProps;
    private final PrMessageRenderer messageRenderer;
    private final EscalationTeamsRegistry escalationTeamsRegistry;

    @Scheduled(cron = "${pr-review-tracking.poll-cron:0 0 9-18 * * 1-5}")
    public void poll() {
        List<PrTrackingRecord> active = prTrackingRepository.findAllActive();
        log.atInfo().addArgument(active::size).log("PR lifecycle poll: {} active records");

        Map<String, Optional<Set<String>>> teamMemberCache = new HashMap<>();

        for (PrTrackingRecord record : active) {
            try {
                processRecord(record, teamMemberCache);
            } catch (Exception e) {
                log.atError()
                        .addArgument(record::repo)
                        .addArgument(record::prNumber)
                        .setCause(e)
                        .log("Error processing PR tracking record for {}#{}, continuing with next record");
            }
        }
    }

    private void processRecord(PrTrackingRecord record, Map<String, Optional<Set<String>>> teamMemberCache) {
        PrMetadata pr;
        try {
            pr = prSourceClients
                    .forProvider(record.provider())
                    .fetchPullRequest(new RepoCoord(record.provider(), record.repo()), record.prNumber());
        } catch (PrSourceException e) {
            log.atWarn()
                    .addArgument(record::repo)
                    .addArgument(record::prNumber)
                    .addArgument(e::getMessage)
                    .log("Could not fetch PR {}#{}: {}");
            return;
        }

        PrTrackingProps.@Nullable Repository repoConfig = findRepoConfig(record.provider(), record.repo());

        if (pr.isClosed()) {
            // Closed/merged PRs skip team-filtering and activity-timestamp updates: the verdict is
            // irrelevant (the closed rows of the table fire regardless of it), so there's no reason to
            // pay the team-membership lookups for a record we're about to terminate.
            apply(record, PrLifecycle.decide(observe(record, pr, null, repoConfig)));
            return;
        }

        List<Review> teamReviews = teamReviewFilter.filterToOwningTeam(pr.reviews(), pr, repoConfig, teamMemberCache);
        Review latestVerdict = teamReviewFilter.findLatestActionableReview(teamReviews);

        apply(record, PrLifecycle.decide(observe(record, pr, latestVerdict, repoConfig)));

        updateActivityTimestamps(record, teamReviews);
    }

    private PrTrackingProps.@Nullable Repository findRepoConfig(Provider provider, String repo) {
        // Key on (provider, name) so a hypothetical name collision across providers can't pick
        // the wrong config — even though PrTrackingProps disallows duplicates today, the lifecycle
        // poller reads from the database and should not assume that constraint as a safety net.
        //
        // Deliberately NOT PrTrackingProps.findRepository(...): prTrackingProps is injected and mocked
        // in PrLifecyclePollerTest, so calling a real (non-stubbed) instance method on that mock would
        // return Mockito's default null rather than running the real lookup — silently breaking every
        // test that stubs repositories() directly. Filter repositories() here instead, which is what the
        // tests actually stub.
        return prTrackingProps.repositories().stream()
                .filter(r -> r.provider() == provider && r.name().equalsIgnoreCase(repo))
                .findFirst()
                .orElse(null);
    }

    // ── Functional core wiring ──

    /**
     * The only impure step: snapshots {@code now()} and derives the SLA-clock fields. {@code
     * remainingForPause} is non-null exactly when there is a live deadline, and {@code slaBreached}
     * can only be true with one — so a no-SLA record never escalates.
     */
    private PrLifecycle.Observation observe(
            PrTrackingRecord record,
            PrMetadata pr,
            @Nullable Review latestVerdict,
            PrTrackingProps.@Nullable Repository repoConfig) {
        Instant now = Instant.now();
        PrLifecycle.Verdict verdict = latestVerdict == null
                ? null
                : latestVerdict.isApproved() ? PrLifecycle.Verdict.APPROVED : PrLifecycle.Verdict.CHANGES_REQUESTED;
        Instant deadline = record.slaDeadline();
        Duration remainingForPause = deadline == null ? null : clampNonNegative(Duration.between(now, deadline));
        boolean slaBreached = deadline != null && now.isAfter(deadline);
        boolean requiresCodeowners = repoConfig != null && repoConfig.requiresCodeowners();
        boolean codeownerApproved = Boolean.TRUE.equals(pr.codeOwnersApproved());
        return new PrLifecycle.Observation(
                record.status(),
                verdict,
                pr.isMergeable(),
                pr.state() == PrMetadata.PrState.MERGED,
                pr.isClosed(),
                slaBreached,
                deadline != null,
                remainingForPause,
                record.slaRemaining(),
                requiresCodeowners,
                codeownerApproved);
    }

    /**
     * The only place that touches the repository / Slack / escalation. Writes the status row implied by
     * {@code (next, slaOp)}, then runs effects in list order. The one exception: {@link
     * PrLifecycle.Effect.Escalate} owns its own status write — it is gated on ticket existence and the
     * escalation result — so the generic write is skipped when one is present.
     */
    private void apply(PrTrackingRecord record, PrLifecycle.Decision decision) {
        boolean escalateOwnsWrite = decision.effects().stream()
                .anyMatch(
                        e -> e instanceof PrLifecycle.Effect.Escalate || e instanceof PrLifecycle.Effect.EscalateMerge);
        switch (decision.slaOp()) {
            case PrLifecycle.SlaOp.Pause pause ->
                prTrackingRepository.pauseSla(record.id(), decision.next(), pause.remaining());
            case PrLifecycle.SlaOp.Resume ignored ->
                prTrackingRepository.resumeSla(record.id(), Instant.now().plus(checkNotNull(record.slaRemaining())));
            case PrLifecycle.SlaOp.Start ignored -> startMergeClock(record, decision.next());
            case PrLifecycle.SlaOp.SetClosedAt ignored -> {
                prTrackingRepository.updateStatus(
                        record.id(), PrTrackingStatus.CLOSED, Instant.now(), record.escalationId());
                log.atInfo()
                        .addArgument(record::repo)
                        .addArgument(record::prNumber)
                        .log("PR {}#{} marked as CLOSED");
            }
            case PrLifecycle.SlaOp.None ignored -> {
                if (decision.next() != record.status() && !escalateOwnsWrite) {
                    prTrackingRepository.updateStatus(record.id(), decision.next(), null, record.escalationId());
                }
            }
        }
        logApprovedAwaitingMerge(record, decision);
        for (PrLifecycle.Effect effect : decision.effects()) {
            runEffect(record, effect);
        }
    }

    /**
     * Starts the merge-chase SLA clock on entry to AWAITING_MERGE. Provisional source: the repo's
     * configured default SLA (the exact merge-SLA source ties into the deferred-clock decision). When
     * the repo has no SLA, the state is entered without a deadline, so it never merge-escalates.
     */
    private void startMergeClock(PrTrackingRecord record, PrTrackingStatus next) {
        Duration sla = mergeSlaFor(record);
        if (sla != null) {
            prTrackingRepository.startSla(record.id(), next, Instant.now().plus(sla));
        } else {
            prTrackingRepository.updateStatus(record.id(), next, null, record.escalationId());
        }
    }

    private @Nullable Duration mergeSlaFor(PrTrackingRecord record) {
        PrTrackingProps.@Nullable Repository repoConfig = findRepoConfig(record.provider(), record.repo());
        if (repoConfig == null || repoConfig.sla() == null) {
            return null;
        }
        return repoConfig.sla().defaultSla();
    }

    /**
     * Restores the per-transition INFO line the old imperative code logged on every transition into
     * {@code APPROVED} (awaiting merge). The {@code APPROVED} rows carry no {@link PrLifecycle.Effect},
     * so unlike the other states their transition would otherwise be silent; the three wordings
     * (SLA-paused / plain / post-escalation) mirror the old {@code handleApproval} and
     * {@code processEscalatedRecord} logs.
     */
    private void logApprovedAwaitingMerge(PrTrackingRecord record, PrLifecycle.Decision decision) {
        if (decision.next() != PrTrackingStatus.APPROVED) {
            return;
        }
        String template;
        if (decision.slaOp() instanceof PrLifecycle.SlaOp.Pause) {
            template = "PR {}#{} approved — SLA paused, awaiting merge";
        } else if (record.status() == PrTrackingStatus.ESCALATED) {
            template = "PR {}#{} approved after escalation — awaiting merge";
        } else {
            template = "PR {}#{} approved — awaiting merge";
        }
        log.atInfo().addArgument(record::repo).addArgument(record::prNumber).log(template);
    }

    private void runEffect(PrTrackingRecord record, PrLifecycle.Effect effect) {
        switch (effect) {
            case PrLifecycle.Effect.NotifyChangesRequested ignored -> notifyChangesRequested(record);
            case PrLifecycle.Effect.NotifyApproved ignored -> notifyClosure(record, MessageEvent.APPROVED);
            case PrLifecycle.Effect.NotifyAwaitingMerge ignored -> notifyAwaitingMerge(record);
            case PrLifecycle.Effect.NotifyClosed notify -> notifyClosure(record, notify.event());
            case PrLifecycle.Effect.Escalate ignored -> escalate(record);
            case PrLifecycle.Effect.EscalateMerge ignored -> escalateMerge(record);
        }
    }

    private static Duration clampNonNegative(Duration d) {
        return d.isNegative() ? Duration.ZERO : d;
    }

    // ── Effects (imperative shell) ──

    private void notifyChangesRequested(PrTrackingRecord record) {
        log.atInfo().addArgument(record::repo).addArgument(record::prNumber).log("PR {}#{} changes requested");

        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn()
                    .addArgument(record::ticketId)
                    .log("Ticket {} not found for changes-requested notification, skipping Slack message");
            return;
        }

        PrMessageContext ctx = recordContext(record);
        String override = messageRenderer.render(record.repo(), MessageEvent.CHANGES_REQUESTED, ctx);
        String message = override != null
                ? override
                : "%s `%s%s%d` has been reviewed and changes have been requested."
                        .formatted(
                                PrTerminology.noun(record.provider()),
                                record.repo(),
                                PrTerminology.separator(record.provider()),
                                record.prNumber());
        postMessage(message, ticket.channelId(), ticket.queryTs(), record);
    }

    /**
     * Posts the closure message (approved / merged / closed) and auto-closes the ticket when this was
     * the last closable PR for it. The status write to {@code CLOSED} has already happened in {@link
     * #apply} via {@link PrLifecycle.SlaOp.SetClosedAt}, so this runs after it — preserving the old
     * "close, post, then auto-close" order.
     */
    private void notifyClosure(PrTrackingRecord record, MessageEvent event) {
        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn()
                    .addArgument(record::ticketId)
                    .log("Ticket {} not found after PR closed, skipping Slack message");
            return;
        }

        postMessage(closureMessage(record, event), ticket.channelId(), ticket.queryTs(), record);

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

    private String closureMessage(PrTrackingRecord record, MessageEvent event) {
        String override = messageRenderer.render(record.repo(), event, recordContext(record));
        if (override != null) {
            return override;
        }
        String verbPhrase =
                switch (event) {
                    case APPROVED -> "approved and is ready to merge";
                    case MERGED -> "merged";
                    case CLOSED -> "closed";
                    default -> throw new IllegalArgumentException("Not a closure event: " + event);
                };
        return "%s `%s%s%d` has been %s. :white_check_mark:"
                .formatted(
                        PrTerminology.noun(record.provider()),
                        record.repo(),
                        PrTerminology.separator(record.provider()),
                        record.prNumber(),
                        verbPhrase);
    }

    /**
     * Review-phase breach (preserved from the old {@code handleSlaBreached}): chases the owning team to
     * review. Delegates to {@link #doEscalate}, which owns the {@code ESCALATED} status write.
     */
    private void escalate(PrTrackingRecord record) {
        doEscalate(
                record,
                MessageEvent.ESCALATED,
                PrTrackingStatus.ESCALATED,
                "SLA breach",
                "PR {}#{} SLA breached — escalated on ticket {}");
    }

    /**
     * Shared escalation flow behind {@link #escalate} (review phase) and {@link #escalateMerge} (merge
     * phase). Compound effect that owns its own status write: it posts the optional custom message before
     * the escalation card, creates the escalation (target = owning team), writes {@code targetStatus} with
     * the new escalation id — or null when creation failed, to avoid reprocessing — and marks the ticket
     * escalated. If the ticket is missing nothing is written, so the record retries on the next poll.
     *
     * <p>Post-before-createEscalation is deliberate so the custom message lands in thread before the
     * escalation card. Poll-time semantics differ from detection-time: here the custom message is ADDED in
     * front of the (unchanged) escalation card; in {@code PrDetectionService} it REPLACES the default
     * breach text. Do not harmonise these without revisiting the spec — the asymmetry is deliberate
     * (poll-time previously posted no tenant-thread text at all, so unset = same as before).
     */
    private void doEscalate(
            PrTrackingRecord record,
            MessageEvent messageEvent,
            PrTrackingStatus targetStatus,
            String breachDescription,
            String breachLogTemplate) {
        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn()
                    .addArgument(record::ticketId)
                    .addArgument(breachDescription)
                    .log("Ticket {} not found for {}, skipping");
            return;
        }

        String escalationOverride = messageRenderer.render(record.repo(), messageEvent, recordContext(record));
        if (escalationOverride != null) {
            postMessage(escalationOverride, ticket.channelId(), ticket.queryTs(), record);
        }

        Escalation escalation = escalationProcessingService.createEscalation(CreateEscalationRequest.builder()
                .ticket(ticket)
                .team(record.owningTeam())
                .tags(ImmutableList.of())
                .source(EscalationSource.bot)
                .build());

        if (escalation == null || escalation.id() == null) {
            log.atWarn()
                    .addArgument(record::repo)
                    .addArgument(record::prNumber)
                    .addArgument(record::ticketId)
                    .addArgument(targetStatus)
                    .log(
                            "Escalation creation returned null for PR {}#{} on ticket {} — marking {} to avoid reprocessing");
            prTrackingRepository.updateStatus(record.id(), targetStatus, null, null);
            return;
        }
        Long escalationId = escalation.id().id();
        prTrackingRepository.updateStatus(record.id(), targetStatus, null, escalationId);
        ticketSlackService.markTicketEscalated(ticket.queryRef());

        log.atInfo()
                .addArgument(record::repo)
                .addArgument(record::prNumber)
                .addArgument(record::ticketId)
                .log(breachLogTemplate);
    }

    /** Notifies the tenant that the code owners have approved and the maintaining team can now merge. */
    private void notifyAwaitingMerge(PrTrackingRecord record) {
        log.atInfo()
                .addArgument(record::repo)
                .addArgument(record::prNumber)
                .log("PR {}#{} approved by code owners — awaiting merge by the maintaining team");

        Ticket ticket = ticketRepository.findTicketById(new TicketId(record.ticketId()));
        if (ticket == null) {
            log.atWarn()
                    .addArgument(record::ticketId)
                    .log("Ticket {} not found for awaiting-merge notification, skipping Slack message");
            return;
        }

        PrMessageContext ctx = recordContext(record);
        String override = messageRenderer.render(record.repo(), MessageEvent.AWAITING_MERGE, ctx);
        String message = override != null
                ? override
                : "%s `%s%s%d` has been approved by its code owners and is ready for the maintaining team to merge."
                        .formatted(
                                PrTerminology.noun(record.provider()),
                                record.repo(),
                                PrTerminology.separator(record.provider()),
                                record.prNumber());
        postMessage(message, ticket.channelId(), ticket.queryTs(), record);
    }

    /**
     * Merge-phase counterpart of {@link #escalate}: chases the maintaining team to merge a PR that the
     * code owners have approved. Delegates to {@link #doEscalate}, which owns the {@code MERGE_ESCALATED}
     * status write (the {@code None} SLA op is skipped because {@code EscalateMerge} is in the
     * escalate-owns-write set).
     */
    private void escalateMerge(PrTrackingRecord record) {
        doEscalate(
                record,
                MessageEvent.MERGE_ESCALATED,
                PrTrackingStatus.MERGE_ESCALATED,
                "merge-SLA breach",
                "PR {}#{} merge SLA breached — escalated maintaining team on ticket {}");
    }

    private void updateActivityTimestamps(PrTrackingRecord record, List<Review> teamReviews) {
        Instant latestReviewAt = teamReviews.stream()
                .map(Review::submittedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (latestReviewAt != null
                && (record.lastReviewAt() == null || latestReviewAt.isAfter(record.lastReviewAt()))) {
            prTrackingRepository.updateActivityTimestamps(record.id(), latestReviewAt, record.lastAuthorActivityAt());
        }
    }

    private PrMessageContext recordContext(PrTrackingRecord record) {
        Duration sla = null;
        Instant slaDeadline = null;
        if (record.slaDeadline() != null) {
            Duration computed = Duration.between(record.prCreatedAt(), record.slaDeadline());
            if (computed.isPositive()) {
                sla = computed;
                slaDeadline = record.slaDeadline();
            } else {
                log.atWarn()
                        .addArgument(record::repo)
                        .addArgument(record::prNumber)
                        .log(
                                "PR {}#{} has non-positive computed SLA duration; omitting sla_duration from message context");
            }
        }
        return new PrMessageContext(
                record.provider(),
                record.repo(),
                record.prNumber(),
                resolveTeamLabel(record.owningTeam()),
                sla,
                slaDeadline);
    }

    private String resolveTeamLabel(String teamCode) {
        EscalationTeam team = escalationTeamsRegistry.findEscalationTeamByCode(teamCode);
        return team != null ? team.label() : teamCode;
    }

    private void postMessage(String text, String channelId, MessageTs queryTs, PrTrackingRecord record) {
        try {
            slackClient.postMessage(new SlackPostMessageRequest(
                    SimpleSlackMessage.builder().text(text).build(), channelId, queryTs));
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(record::repo)
                    .addArgument(record::prNumber)
                    .addArgument(record::ticketId)
                    .log("Failed to post Slack message for PR {}#{} on ticket {}, continuing");
        }
    }
}
