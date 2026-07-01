package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.prtracking.source.PrMetadata;
import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import com.coreeng.supportbot.prtracking.source.PrSourceClients;
import com.coreeng.supportbot.prtracking.source.PrSourceException;
import com.coreeng.supportbot.prtracking.source.Provider;
import com.coreeng.supportbot.prtracking.source.RepoCoord;
import com.coreeng.supportbot.prtracking.source.Review;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrLifecyclePollerTest {

    @Mock
    private PrTrackingRepository prTrackingRepository;

    @Mock
    private PrSourceClients prSourceClients;

    // Reached via prSourceClients.forProvider(GITHUB) (stubbed in setUp), not constructor-injected.
    @SuppressWarnings("MockNotUsedInProduction")
    @Mock
    private PrSourceClient prSourceClient;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketProcessingService ticketProcessingService;

    @Mock
    private EscalationProcessingService escalationProcessingService;

    @Mock
    private TicketSlackService ticketSlackService;

    @Mock
    private SlackClient slackClient;

    @Mock
    private PrTrackingProps prTrackingProps;

    @Mock
    private SlaLookup slaLookup;

    @Mock
    private PrMessageRenderer messageRenderer;

    @Mock
    private EscalationTeamsRegistry escalationTeamsRegistry;

    // Tracks every PrTrackingRecord built by a test (via record()/pausedRecord()/register()), keyed by
    // id, so the generic write-method stubs below can return an updated row — mirroring
    // JdbcPrTrackingRepository, which returns the post-write row from `RETURNING` — instead of Mockito's
    // default null. PrLifecyclePoller.apply() now threads that returned record into the effects it runs
    // (see the real-fix commit for #11: an effect like NotifyAwaitingMerge must see the deadline this
    // transition just wrote, not a stale pre-write value), so a null return would NPE inside the effect.
    private final Map<Long, PrTrackingRecord> knownRecords = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(prSourceClients.forProvider(Provider.GITHUB)).thenReturn(prSourceClient);
        lenient()
                .when(prTrackingRepository.updateStatus(anyLong(), any(), any(), any()))
                .thenAnswer(inv -> register(withStatus(
                        known(inv.getArgument(0)), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3))));
        lenient()
                .when(prTrackingRepository.pauseSla(anyLong(), any(), any()))
                .thenAnswer(
                        inv -> register(withPause(known(inv.getArgument(0)), inv.getArgument(1), inv.getArgument(2))));
        lenient()
                .when(prTrackingRepository.resumeSla(anyLong(), any()))
                .thenAnswer(inv -> register(withResume(known(inv.getArgument(0)), inv.getArgument(1))));
        lenient()
                .when(prTrackingRepository.startSla(anyLong(), any(), any()))
                .thenAnswer(
                        inv -> register(withStart(known(inv.getArgument(0)), inv.getArgument(1), inv.getArgument(2))));
    }

    private PrTrackingRecord register(PrTrackingRecord record) {
        knownRecords.put(record.id(), record);
        return record;
    }

    private PrTrackingRecord known(long id) {
        PrTrackingRecord record = knownRecords.get(id);
        if (record == null) {
            throw new IllegalStateException(
                    "No PrTrackingRecord registered for id " + id + " — build it via record()/pausedRecord()/register()"
                            + " so the repository write stubs can return an updated row");
        }
        return record;
    }

    private static PrTrackingRecord withStatus(
            PrTrackingRecord base, PrTrackingStatus status, @Nullable Instant closedAt, @Nullable Long escalationId) {
        boolean closed = status == PrTrackingStatus.CLOSED;
        return new PrTrackingRecord(
                base.id(),
                base.ticketId(),
                base.provider(),
                base.repo(),
                base.prNumber(),
                base.prCreatedAt(),
                closed ? null : base.slaDeadline(),
                base.owningTeam(),
                base.canAutoCloseTicket(),
                status,
                escalationId,
                closedAt,
                closed ? null : base.slaRemaining(),
                base.lastReviewAt(),
                base.lastAuthorActivityAt());
    }

    private static PrTrackingRecord withPause(PrTrackingRecord base, PrTrackingStatus status, Duration remaining) {
        return new PrTrackingRecord(
                base.id(),
                base.ticketId(),
                base.provider(),
                base.repo(),
                base.prNumber(),
                base.prCreatedAt(),
                null,
                base.owningTeam(),
                base.canAutoCloseTicket(),
                status,
                base.escalationId(),
                base.closedAt(),
                remaining,
                base.lastReviewAt(),
                base.lastAuthorActivityAt());
    }

    private static PrTrackingRecord withResume(PrTrackingRecord base, Instant newDeadline) {
        return new PrTrackingRecord(
                base.id(),
                base.ticketId(),
                base.provider(),
                base.repo(),
                base.prNumber(),
                base.prCreatedAt(),
                newDeadline,
                base.owningTeam(),
                base.canAutoCloseTicket(),
                PrTrackingStatus.OPEN,
                base.escalationId(),
                base.closedAt(),
                null,
                base.lastReviewAt(),
                base.lastAuthorActivityAt());
    }

    private static PrTrackingRecord withStart(PrTrackingRecord base, PrTrackingStatus status, Instant newDeadline) {
        return new PrTrackingRecord(
                base.id(),
                base.ticketId(),
                base.provider(),
                base.repo(),
                base.prNumber(),
                base.prCreatedAt(),
                newDeadline,
                base.owningTeam(),
                base.canAutoCloseTicket(),
                status,
                base.escalationId(),
                base.closedAt(),
                null,
                base.lastReviewAt(),
                base.lastAuthorActivityAt());
    }

    // ── Existing behaviour (PR closed/merged, escalation) ──

    @Test
    void pollContinuesWhenOneRecordProcessingThrowsRuntimeException() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord first = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        PrTrackingRecord second = record(
                2L,
                200L,
                "my-org/repo-b",
                22,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));

        when(prTrackingRepository.findAllActive()).thenReturn(List.of(first, second));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(first.repo()), first.prNumber()))
                .thenReturn(closedPr(first));
        when(ticketRepository.findTicketById(new TicketId(first.ticketId())))
                .thenThrow(new RuntimeException("database temporarily unavailable"));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(second.repo()), second.prNumber()))
                .thenReturn(openPr(second));

        // when / then
        assertDoesNotThrow(poller::poll);
        verify(prSourceClient).fetchPullRequest(RepoCoord.github(first.repo()), first.prNumber());
        verify(prSourceClient).fetchPullRequest(RepoCoord.github(second.repo()), second.prNumber());
    }

    @Test
    void closesTicketWhenClosedPrIsLastActiveRecord() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        Ticket ticket = ticket(100L);
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket);
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(false);
        when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
        when(prTrackingProps.impact()).thenReturn("low");

        // when
        poller.poll();

        // then
        verify(ticketProcessingService)
                .closeForPrResolution(eq(new TicketId(100L)), eq(ImmutableList.of("pr-review")), eq("low"));
    }

    @Test
    void closesTicketEvenWhenClosureSlackMessageFails() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        Ticket ticket = ticket(100L);
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket);
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(false);
        when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
        when(prTrackingProps.impact()).thenReturn("low");
        when(slackClient.postMessage(any())).thenThrow(new RuntimeException("slack down"));

        // when
        poller.poll();

        // then
        verify(prTrackingRepository).updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(), eq(null));
        verify(ticketProcessingService)
                .closeForPrResolution(eq(new TicketId(100L)), eq(ImmutableList.of("pr-review")), eq("low"));
    }

    @Test
    void doesNotCloseTicketWhenOtherActiveRecordsRemain() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);

        // when
        poller.poll();

        // then
        verify(ticketProcessingService, never()).closeForPrResolution(any(), any(), any());
    }

    @Test
    void skipsTicketCloseWhenTicketNotFoundForClosedPr() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(null);

        // when
        poller.poll();

        // then
        verify(prTrackingRepository)
                .updateStatus(
                        eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), eq(record.escalationId()));
        verify(ticketProcessingService, never()).closeForPrResolution(any(), any(), any());
    }

    @Test
    void marksTrackingEscalatedWhenSlaEscalationReturnsNull() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().minusSeconds(60));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(openPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(escalationProcessingService.createEscalation(any())).thenReturn(null);

        // when
        poller.poll();

        // then — record must be moved to ESCALATED to prevent infinite poller loop
        verify(prTrackingRepository).updateStatus(eq(record.id()), eq(PrTrackingStatus.ESCALATED), isNull(), isNull());
        verify(ticketSlackService, never()).markTicketEscalated(any());
    }

    @Test
    void pollTimeBreachPostsNoTenantMessageWhenNotConfigured() {
        // given breach scenario, no escalation-message configured
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().minusSeconds(60));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(openPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(escalationProcessingService.createEscalation(any()))
                .thenReturn(Escalation.builder().id(new EscalationId(500L)).build());
        when(prTrackingProps.repositories())
                .thenReturn(List.of(new PrTrackingProps.Repository(
                        "my-org/repo-a",
                        "wow",
                        null,
                        List.of(),
                        new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));

        // when
        poller.poll();

        // then escalation fires as normal but no tenant-thread post
        verify(prTrackingRepository).updateStatus(eq(record.id()), eq(PrTrackingStatus.ESCALATED), isNull(), eq(500L));
        verify(ticketSlackService).markTicketEscalated(any());
        verify(slackClient, never()).postMessage(any());
    }

    @Test
    void pollTimeBreachPostsCustomMessageWhenConfigured() {
        // given same breach scenario with custom escalated message override
        String customMessage = "Contact #pr-reviews to chase this review.";
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                11,
                PrTrackingStatus.OPEN,
                Instant.now().minusSeconds(60));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(openPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(escalationProcessingService.createEscalation(any()))
                .thenReturn(Escalation.builder().id(new EscalationId(500L)).build());
        when(prTrackingProps.repositories())
                .thenReturn(List.of(new PrTrackingProps.Repository(
                        "my-org/repo-a",
                        "wow",
                        null,
                        List.of(),
                        new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
        when(messageRenderer.render(eq("my-org/repo-a"), eq(MessageEvent.ESCALATED), any()))
                .thenReturn(customMessage);

        // when
        poller.poll();

        // then escalation fires AND the custom message is posted in the tenant thread
        verify(prTrackingRepository).updateStatus(eq(record.id()), eq(PrTrackingStatus.ESCALATED), isNull(), eq(500L));
        verify(ticketSlackService).markTicketEscalated(any());
        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).isEqualTo(customMessage);
    }

    @Test
    void doesNotAutoCloseTicketForReplyOriginTrackingRecord() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = register(new PrTrackingRecord(
                1L,
                100L,
                Provider.GITHUB,
                "my-org/repo-a",
                11,
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "wow",
                false,
                PrTrackingStatus.OPEN,
                null,
                null,
                null,
                null,
                null));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));

        // when
        poller.poll();

        // then
        verify(ticketProcessingService, never()).closeForPrResolution(any(), any(), any());
        verify(prTrackingRepository, never()).hasAnyActiveClosableForTicket(anyLong());
    }

    @Test
    void postsMergedMessageWhenPrIsMerged() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                42,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(mergedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);

        // when
        poller.poll();

        // then
        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).contains("has been merged");
    }

    @Test
    void postsClosedMessageWhenPrIsClosed() {
        // given
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                42,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);

        // when
        poller.poll();

        // then
        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).contains("has been closed");
    }

    @Test
    void postsCustomMergedMessageWhenOverrideConfigured() {
        String customMessage = "PR merged — thanks for the contribution!";
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                42,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(mergedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);
        when(messageRenderer.render(eq("my-org/repo-a"), eq(MessageEvent.MERGED), any()))
                .thenReturn(customMessage);

        poller.poll();

        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).isEqualTo(customMessage);
    }

    @Test
    void postsCustomClosedMessageWhenOverrideConfigured() {
        String customMessage = "PR closed without merging.";
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                42,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(closedPr(record));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);
        when(messageRenderer.render(eq("my-org/repo-a"), eq(MessageEvent.CLOSED), any()))
                .thenReturn(customMessage);

        poller.poll();

        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).isEqualTo(customMessage);
    }

    @Test
    void postsCustomApprovedMessageWhenOverrideConfigured() {
        String customMessage = "PR approved — ready to land.";
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                42,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(openMergeablePrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);
        when(messageRenderer.render(eq("my-org/repo-a"), eq(MessageEvent.APPROVED), any()))
                .thenReturn(customMessage);

        poller.poll();

        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).isEqualTo(customMessage);
    }

    @Test
    void postsCustomChangesRequestedMessageWhenOverrideConfigured() {
        String customMessage = "Reviewer requested changes — please address the feedback.";
        PrLifecyclePoller poller = createPoller();
        PrTrackingRecord record = record(
                1L,
                100L,
                "my-org/repo-a",
                42,
                PrTrackingStatus.OPEN,
                Instant.now().plusSeconds(7200));
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(messageRenderer.render(eq("my-org/repo-a"), eq(MessageEvent.CHANGES_REQUESTED), any()))
                .thenReturn(customMessage);

        poller.poll();

        ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(captor.capture());
        assertThat(captor.getValue().message().getText()).isEqualTo(customMessage);
    }

    // ── OPEN state transitions ──

    @Nested
    class OpenState {

        @Test
        void pausesSlaOnChangesRequestedAndNotifiesThread() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .pauseSla(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), any(Duration.class));
            ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
            verify(slackClient).postMessage(captor.capture());
            assertThat(captor.getValue().message().getText()).contains("reviewed and changes have been requested");
        }

        @Test
        void closesOnApprovedAndMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(false);
            when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
            when(prTrackingProps.impact()).thenReturn("low");

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), isNull());
            verify(ticketProcessingService)
                    .closeForPrResolution(eq(new TicketId(100L)), eq(ImmutableList.of("pr-review")), eq("low"));
        }

        @Test
        void postsApprovalMessageOnApprovedAndMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    42,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(true);

            // when
            poller.poll();

            // then
            ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
            verify(slackClient).postMessage(captor.capture());
            assertThat(captor.getValue().message().getText()).contains("approved and is ready to merge");
        }

        @Test
        void pausesSlaOnApprovedButNotMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
        }

        @Test
        void noChangeOnOpenWithNoReviewsAndSlaNotExpired() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPr(record));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(prTrackingRepository, never()).resumeSla(anyLong(), any());
        }

        @Test
        void doesNotThrowWhenSlaDeadlineIsNull() {
            // given — OPEN record with null slaDeadline, no reviews
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = register(new PrTrackingRecord(
                    1L,
                    100L,
                    Provider.GITHUB,
                    "my-org/repo-a",
                    11,
                    Instant.now().minusSeconds(3600),
                    null,
                    "wow",
                    true,
                    PrTrackingStatus.OPEN,
                    null,
                    null,
                    null,
                    null,
                    null));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPr(record));

            // when
            poller.poll();

            // then — no crash, no state changes
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
        }

        @Test
        void neverEscalatesNoSlaPrNoMatterHowOld() {
            // Locks the load-bearing null guard at PrLifecyclePoller.java:133 (`slaDeadline != null &&
            // Instant.now().isAfter(slaDeadline)`). Removing it would NPE (or, with a naive
            // rewrite, blanket-escalate) every no-SLA PR. Ancient createdAt ensures this isn't
            // passing only because the PR is young.
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord ancientNoSlaRecord = register(new PrTrackingRecord(
                    1L,
                    100L,
                    Provider.GITHUB,
                    "my-org/repo-a",
                    11,
                    Instant.now().minus(Duration.ofDays(365)), // a year old, well past any SLA
                    null, // no slaDeadline → no SLA
                    "wow",
                    true,
                    PrTrackingStatus.OPEN,
                    null,
                    null,
                    null,
                    null,
                    null));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(ancientNoSlaRecord));
            when(prSourceClient.fetchPullRequest(
                            RepoCoord.github(ancientNoSlaRecord.repo()), ancientNoSlaRecord.prNumber()))
                    .thenReturn(openPr(ancientNoSlaRecord));

            poller.poll();

            verify(prTrackingRepository, never()).updateStatus(anyLong(), eq(PrTrackingStatus.ESCALATED), any(), any());
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void changesRequestedTransitionForNoSlaPr() {
            // given — OPEN record with null slaDeadline but a CHANGES_REQUESTED review present
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = register(new PrTrackingRecord(
                    1L,
                    100L,
                    Provider.GITHUB,
                    "my-org/repo-a",
                    11,
                    Instant.now().minusSeconds(3600),
                    null,
                    "wow",
                    true,
                    PrTrackingStatus.OPEN,
                    null,
                    null,
                    null,
                    null,
                    null));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(
                            eq(record.id()),
                            eq(PrTrackingStatus.CHANGES_REQUESTED),
                            isNull(),
                            eq(record.escalationId()));
            ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
            verify(slackClient).postMessage(captor.capture());
            assertThat(captor.getValue().message().getText())
                    .contains("PR `%s#%d` has been reviewed and changes have been requested."
                            .formatted(record.repo(), record.prNumber()));
        }

        @Test
        void approvedTransitionForNoSlaUpdatesStatusWithoutPausingSla() {
            // given — OPEN no-SLA record (null slaDeadline) with an APPROVED review present
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = register(new PrTrackingRecord(
                    1L,
                    100L,
                    Provider.GITHUB,
                    "my-org/repo-a",
                    11,
                    Instant.now().minusSeconds(3600),
                    null,
                    "wow",
                    true,
                    PrTrackingStatus.OPEN,
                    null,
                    null,
                    null,
                    null,
                    null));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));

            // when
            poller.poll();

            // then — status is updated to APPROVED but pauseSla is skipped (no SLA to pause) and no Slack notification
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(prTrackingRepository).updateStatus(eq(1L), eq(PrTrackingStatus.APPROVED), isNull(), isNull());
            verify(slackClient, never()).postMessage(any());
        }

        @Test
        void ignoresCommentedReviewsForStateTransition() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.COMMENTED))));

            // when
            poller.poll();

            // then — COMMENTED is not actionable
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
        }

        @Test
        void usesLatestReviewWhenMultipleExist() {
            // given — older CHANGES_REQUESTED, newer APPROVED
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(
                                    review(
                                            Review.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(3600)),
                                    review(
                                            Review.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(60)))));

            // when
            poller.poll();

            // then — APPROVED is latest, so SLA paused as APPROVED (PR not mergeable)
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
        }
    }

    // ── CHANGES_REQUESTED state transitions ──

    @Nested
    class ChangesRequestedState {

        @Test
        void closesOnApprovedAndMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = pausedRecord(
                    1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.CHANGES_REQUESTED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(false);
            when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
            when(prTrackingProps.impact()).thenReturn("low");

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), isNull());
            verify(ticketProcessingService).closeForPrResolution(any(), any(), any());
        }

        @Test
        void transitionsToApprovedWhenNotMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = pausedRecord(
                    1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.CHANGES_REQUESTED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));

            // when
            poller.poll();

            // then — SLA already paused, just status change
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.APPROVED), isNull(), isNull());
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
        }

        @Test
        void resumesSlaWhenNoActionableReviewsRemain() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = pausedRecord(
                    1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.CHANGES_REQUESTED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.DISMISSED))));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository).resumeSla(eq(record.id()), any(Instant.class));
        }

        @Test
        void staysInChangesRequestedWhenLatestVerdictIsChangesRequested() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = pausedRecord(
                    1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.CHANGES_REQUESTED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));

            // when
            poller.poll();

            // then — no transitions
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(prTrackingRepository, never()).resumeSla(anyLong(), any());
        }
    }

    // ── APPROVED state transitions ──

    @Nested
    class ApprovedState {

        @Test
        void closesWhenMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record =
                    pausedRecord(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.APPROVED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(false);
            when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
            when(prTrackingProps.impact()).thenReturn("low");

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), isNull());
            verify(ticketProcessingService).closeForPrResolution(any(), any(), any());
        }

        @Test
        void staysApprovedWhenNotYetMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record =
                    pausedRecord(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.APPROVED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));

            // when
            poller.poll();

            // then — no transitions
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
        }

        @Test
        void transitionsToChangesRequestedWhenReviewerReRequestsChanges() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record =
                    pausedRecord(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.APPROVED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), isNull(), isNull());
        }

        @Test
        void closesWhenPrIsMerged() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record =
                    pausedRecord(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.APPROVED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(mergedPr(record));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(true);

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), isNull());
        }
    }

    // ── ESCALATED state transitions ──

    @Nested
    class EscalatedState {

        @Test
        void closesOnApprovedAndMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.ESCALATED,
                    Instant.now().minusSeconds(60));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(false);
            when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
            when(prTrackingProps.impact()).thenReturn("low");

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), isNull());
        }

        @Test
        void transitionsToApprovedWhenNotMergeable() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.ESCALATED,
                    Instant.now().minusSeconds(60));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.APPROVED), isNull(), isNull());
        }

        @Test
        void skipsSlaEscalationWhenRecordAlreadyEscalated() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.ESCALATED,
                    Instant.now().minusSeconds(60));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPr(record));

            // when
            poller.poll();

            // then
            verifyNoInteractions(escalationProcessingService);
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
        }

        @Test
        void transitionsToChangesRequestedAfterEscalationAndNotifiesThread() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.ESCALATED,
                    Instant.now().minusSeconds(60));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), isNull(), isNull());
            ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
            verify(slackClient).postMessage(captor.capture());
            assertThat(captor.getValue().message().getText()).contains("reviewed and changes have been requested");
        }
    }

    // ── Team filtering ──

    @Nested
    class TeamFiltering {

        @Test
        void filtersReviewsToOwningTeamWhenConfigured() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(
                                    // Non-team member approves, team member requests changes
                                    new Review(
                                            "outsider",
                                            Review.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(60)),
                                    new Review(
                                            "team-member",
                                            Review.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(30)))));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            "platform-team",
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
            when(prSourceClient.resolveTeamMembers(RepoCoord.github("my-org/repo-a"), "platform-team"))
                    .thenReturn(List.of("team-member"));

            // when
            poller.poll();

            // then — only team-member's CHANGES_REQUESTED counts; outsider's APPROVED is ignored
            verify(prTrackingRepository)
                    .pauseSla(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), any(Duration.class));
        }

        @Test
        void skipsTeamFilteringWhenNoGithubTeamSlugConfigured() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            null,
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));

            // when
            poller.poll();

            // then — no team filtering, all reviews count
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
            verify(prSourceClient, never()).resolveTeamMembers(any(), any());
        }

        @Test
        void fallsBackToAllReviewsWhenTeamMemberFetchFails() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            "platform-team",
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
            when(prSourceClient.resolveTeamMembers(RepoCoord.github("my-org/repo-a"), "platform-team"))
                    .thenThrow(new PrSourceException("forbidden"));

            // when
            poller.poll();

            // then — graceful fallback, all reviews count
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
        }

        @Test
        void cachesTeamMembersAcrossRecords() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord first = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            PrTrackingRecord second = record(
                    2L,
                    200L,
                    "my-org/repo-a",
                    22,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(first, second));
            when(prSourceClient.fetchPullRequest(any(), eq(11))).thenReturn(openPr(first));
            when(prSourceClient.fetchPullRequest(any(), eq(22))).thenReturn(openPr(second));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            "platform-team",
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
            when(prSourceClient.resolveTeamMembers(RepoCoord.github("my-org/repo-a"), "platform-team"))
                    .thenReturn(List.of("team-member"));

            // when
            poller.poll();

            // then — team members fetched only once
            verify(prSourceClient, org.mockito.Mockito.times(1))
                    .resolveTeamMembers(RepoCoord.github("my-org/repo-a"), "platform-team");
        }
    }

    // ── Requested teams fallback ──

    @Nested
    class RequestedTeamsFallback {

        @Test
        void filtersReviewsUsingRequestedTeamMembersWhenNoSlugConfigured() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithRequestedReviewers(
                            record,
                            List.of("auto-reviewer"),
                            List.of(
                                    new Review(
                                            "outsider",
                                            Review.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(60)),
                                    new Review(
                                            "auto-reviewer",
                                            Review.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(30)))));
            // No githubTeamSlug, no repo config
            when(prTrackingProps.repositories()).thenReturn(List.of());

            // when
            poller.poll();

            // then — outsider's APPROVED ignored, auto-reviewer's CHANGES_REQUESTED applies
            verify(prTrackingRepository)
                    .pauseSla(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), any(Duration.class));
        }

        @Test
        void acceptsAllReviewsWhenNoRequestedTeamsAndNoSlug() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(prTrackingProps.repositories()).thenReturn(List.of());

            // when
            poller.poll();

            // then — no teams requested, all reviews accepted
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
        }

        @Test
        void acceptsAllReviewsWhenRequestedTeamMembershipUnresolved() {
            // given — the source client couldn't list a requested team's members (null, not a partial list;
            // see Hub4jGitHubClient.resolveRequestedTeamMembers). TeamReviewFilter must treat this as
            // "cannot verify" — same as an explicit team-slug lookup failure — and accept all reviews,
            // rather than filtering against whatever partial data happened to resolve.
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithUnresolvedRequestedReviewers(
                            record, List.of(review(Review.ReviewState.APPROVED))));
            // No githubTeamSlug, no repo config
            when(prTrackingProps.repositories()).thenReturn(List.of());

            // when
            poller.poll();

            // then — the reviewer's team membership couldn't be confirmed, but their review still counts
            // rather than being silently dropped by a would-be-incomplete member set.
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
        }
    }

    // ── Codeowner merge clock (SlaLookup wiring) ──

    @Nested
    class CodeownerMergeClock {

        @Test
        void startsMergeClockUsingSlaLookupResolutionNotRawConfigDefault() {
            // given — the repo's inline default is 24h, but SlaLookup (file / path-override resolution)
            // says 6h for this PR. The merge clock must reflect SlaLookup's answer, proving the poller
            // calls through to it rather than reading repoConfig.sla().defaultSla() directly.
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.OPEN, null);
            PrTrackingProps.Repository repoConfig = codeownerRepoConfig(Duration.ofHours(24));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openCodeownerApprovedMergeablePr(record));
            when(prTrackingProps.repositories()).thenReturn(List.of(repoConfig));
            when(slaLookup.getSla(eq(repoConfig), any(RepoCoord.class), eq(record.prNumber())))
                    .thenReturn(Duration.ofHours(6));

            // when
            poller.poll();

            // then
            ArgumentCaptor<Instant> deadlineCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(prTrackingRepository)
                    .startSla(eq(record.id()), eq(PrTrackingStatus.AWAITING_MERGE), deadlineCaptor.capture());
            assertThat(deadlineCaptor.getValue())
                    .isCloseTo(Instant.now().plus(Duration.ofHours(6)), within(Duration.ofSeconds(30)));
        }

        @Test
        void entersAwaitingMergeWithoutDeadlineWhenSlaLookupReturnsNull() {
            // given — SlaLookup found no default, no file, no matching override: no merge SLA is
            // determinable. The state is still entered (chasing the code owner's approval already
            // happened), just without a deadline — mirroring "repo has no SLA" for the review phase.
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.OPEN, null);
            PrTrackingProps.Repository repoConfig = codeownerRepoConfig(Duration.ofHours(24));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openCodeownerApprovedMergeablePr(record));
            when(prTrackingProps.repositories()).thenReturn(List.of(repoConfig));
            when(slaLookup.getSla(eq(repoConfig), any(RepoCoord.class), eq(record.prNumber())))
                    .thenReturn(null);

            // when
            poller.poll();

            // then — status still advances, but via the no-deadline path
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.AWAITING_MERGE), isNull(), any());
            verify(prTrackingRepository, never()).startSla(anyLong(), any(), any());
        }

        @Test
        void notifyAwaitingMergeRendersTheFreshMergeDeadlineNotAStaleReviewDeadline() {
            // given — the record still carries a live review-phase deadline from before code owners
            // approved (e.g. a repo whose review SLA hasn't been reached yet). NotifyAwaitingMerge fires
            // in the same decide() as the Start op that overwrites this deadline with the merge-phase
            // one — the notification must render off the value just written, not this stale one.
            PrLifecyclePoller poller = createPoller();
            Instant staleReviewDeadline = Instant.now().plus(Duration.ofDays(30));
            PrTrackingRecord record = record(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.OPEN, staleReviewDeadline);
            PrTrackingProps.Repository repoConfig = codeownerRepoConfig(Duration.ofHours(24));
            Ticket ticket = ticket(100L);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openCodeownerApprovedMergeablePr(record));
            when(prTrackingProps.repositories()).thenReturn(List.of(repoConfig));
            when(slaLookup.getSla(eq(repoConfig), any(RepoCoord.class), eq(record.prNumber())))
                    .thenReturn(Duration.ofHours(6));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket);

            // when
            poller.poll();

            // then — the rendered context's sla/slaDeadline reflect the merge clock just started, not
            // the pre-transition review deadline
            ArgumentCaptor<PrMessageContext> ctxCaptor = ArgumentCaptor.forClass(PrMessageContext.class);
            verify(messageRenderer).render(eq(record.repo()), eq(MessageEvent.AWAITING_MERGE), ctxCaptor.capture());
            PrMessageContext ctx = ctxCaptor.getValue();
            assertThat(ctx.sla()).isNotNull().isCloseTo(Duration.ofHours(6), Duration.ofSeconds(30));
            assertThat(ctx.slaDeadline())
                    .isNotNull()
                    .isCloseTo(Instant.now().plus(Duration.ofHours(6)), within(Duration.ofSeconds(30)));
        }

        @Test
        void leavesRecordUnchangedWhenSlaLookupFails() {
            // given — the SLA file couldn't be fetched (provider hiccup). The transition must not
            // partially apply: no status write, and no "ready to merge" notification either (which would
            // otherwise fire even though the clock never started). poll() retries the whole transition,
            // SlaLookup call included, on the next poll.
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.OPEN, null);
            PrTrackingProps.Repository repoConfig = codeownerRepoConfig(Duration.ofHours(24));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openCodeownerApprovedMergeablePr(record));
            when(prTrackingProps.repositories()).thenReturn(List.of(repoConfig));
            when(slaLookup.getSla(eq(repoConfig), any(RepoCoord.class), eq(record.prNumber())))
                    .thenThrow(new PrSourceException("SLA file fetch failed"));

            // when — the per-record catch in poll() swallows the failure; it must not propagate
            assertDoesNotThrow(poller::poll);

            // then — no write and no notification for this poll
            verify(prTrackingRepository, never()).startSla(anyLong(), any(), any());
            verify(prTrackingRepository, never())
                    .updateStatus(anyLong(), eq(PrTrackingStatus.AWAITING_MERGE), any(), any());
            verifyNoInteractions(slackClient);
        }

        private PrTrackingProps.Repository codeownerRepoConfig(Duration defaultSla) {
            return new PrTrackingProps.Repository(
                    "my-org/repo-a",
                    "wow",
                    Provider.GITHUB,
                    null,
                    null,
                    List.of(),
                    new PrTrackingProps.Sla(null, defaultSla, null),
                    null,
                    null,
                    List.of(),
                    true,
                    false);
        }

        private PrMetadata openCodeownerApprovedMergeablePr(PrTrackingRecord record) {
            return new PrMetadata(
                    RepoCoord.github(record.repo()),
                    record.prNumber(),
                    record.prCreatedAt(),
                    PrMetadata.PrState.OPEN,
                    true,
                    List.of(),
                    List.of(),
                    null,
                    true,
                    List.of());
        }
    }

    // ── Activity timestamps ──

    @Nested
    class ActivityTimestamps {

        @Test
        void updatesLastReviewAtFromLatestReview() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            Instant reviewTime = Instant.parse("2026-03-20T12:00:00Z");
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(
                                    review(Review.ReviewState.COMMENTED, reviewTime.minusSeconds(3600)),
                                    review(Review.ReviewState.APPROVED, reviewTime))));

            // when
            poller.poll();

            // then — last_review_at is the latest review timestamp (APPROVED)
            verify(prTrackingRepository).updateActivityTimestamps(eq(record.id()), eq(reviewTime), isNull());
        }

        @Test
        void doesNotUpdateTimestampsWhenNoReviews() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPr(record));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository, never()).updateActivityTimestamps(anyLong(), any(), any());
        }

        @Test
        void doesNotUpdateWhenReviewIsOlderThanExisting() {
            // given
            PrLifecyclePoller poller = createPoller();
            Instant existingReviewTime = Instant.parse("2026-03-20T12:00:00Z");
            PrTrackingRecord record = register(new PrTrackingRecord(
                    1L,
                    100L,
                    Provider.GITHUB,
                    "my-org/repo-a",
                    11,
                    Instant.now().minusSeconds(3600),
                    Instant.now().plusSeconds(7200),
                    "wow",
                    true,
                    PrTrackingStatus.OPEN,
                    null,
                    null,
                    null,
                    existingReviewTime,
                    null));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(review(Review.ReviewState.COMMENTED, existingReviewTime.minusSeconds(60)))));

            // when
            poller.poll();

            // then — review is older, no update
            verify(prTrackingRepository, never()).updateActivityTimestamps(anyLong(), any(), any());
        }
    }

    // ── Edge cases ──

    @Nested
    class EdgeCases {

        @Test
        void resumeSlaToOpenSkipsWhenSlaRemainingIsNull() {
            // given — CHANGES_REQUESTED record with null slaRemaining (data inconsistency)
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = register(new PrTrackingRecord(
                    1L,
                    100L,
                    Provider.GITHUB,
                    "my-org/repo-a",
                    11,
                    Instant.now().minusSeconds(3600),
                    null,
                    "wow",
                    true,
                    PrTrackingStatus.CHANGES_REQUESTED,
                    null,
                    null,
                    null,
                    null,
                    null));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.DISMISSED))));

            // when
            poller.poll();

            // then — no crash, no resumeSla call
            verify(prTrackingRepository, never()).resumeSla(anyLong(), any());
        }

        @Test
        void changesRequestedWithEmptyReviewListResumesSla() {
            // given — CHANGES_REQUESTED record, empty review list
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = pausedRecord(
                    1L, 100L, "my-org/repo-a", 11, PrTrackingStatus.CHANGES_REQUESTED, Duration.ofHours(4));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPr(record));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository).resumeSla(eq(record.id()), any(Instant.class));
        }

        @Test
        void escalatedRecordClosesWhenPrMerged() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.ESCALATED,
                    Instant.now().minusSeconds(60));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(mergedPr(record));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));
            when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                    .thenReturn(true);

            // when
            poller.poll();

            // then — closed via handlePrClosed, not processEscalatedRecord
            verify(prTrackingRepository)
                    .updateStatus(eq(record.id()), eq(PrTrackingStatus.CLOSED), any(Instant.class), isNull());
        }

        @Test
        void notifyChangesRequestedSkipsSlackWhenTicketNotFound() {
            // given
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().plusSeconds(7200));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(null);

            // when
            poller.poll();

            // then — SLA paused but no Slack message
            verify(prTrackingRepository)
                    .pauseSla(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), any(Duration.class));
            verify(slackClient, never()).postMessage(any());
        }

        @Test
        void computeRemainingClampsToZeroWhenDeadlineIsPast() {
            // given — OPEN record with past deadline and CHANGES_REQUESTED review
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = record(
                    1L,
                    100L,
                    "my-org/repo-a",
                    11,
                    PrTrackingStatus.OPEN,
                    Instant.now().minusSeconds(60));
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.repo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));

            // when
            poller.poll();

            // then — pauses with non-negative duration
            ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(prTrackingRepository)
                    .pauseSla(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), durationCaptor.capture());
            assertThat(durationCaptor.getValue()).isEqualTo(Duration.ZERO);
        }
    }

    // ── Helpers ──

    private PrLifecyclePoller createPoller() {
        return new PrLifecyclePoller(
                prTrackingRepository,
                prSourceClients,
                new TeamReviewFilter(prSourceClients),
                ticketRepository,
                ticketProcessingService,
                escalationProcessingService,
                ticketSlackService,
                slackClient,
                prTrackingProps,
                slaLookup,
                messageRenderer,
                escalationTeamsRegistry);
    }

    private PrTrackingRecord record(
            long id, long ticketId, String repo, int prNumber, PrTrackingStatus status, @Nullable Instant slaDeadline) {
        return register(new PrTrackingRecord(
                id,
                ticketId,
                Provider.GITHUB,
                repo,
                prNumber,
                Instant.now().minusSeconds(3600),
                slaDeadline,
                "wow",
                true,
                status,
                null,
                null,
                null,
                null,
                null));
    }

    private PrTrackingRecord pausedRecord(
            long id, long ticketId, String repo, int prNumber, PrTrackingStatus status, Duration slaRemaining) {
        return register(new PrTrackingRecord(
                id,
                ticketId,
                Provider.GITHUB,
                repo,
                prNumber,
                Instant.now().minusSeconds(3600),
                null,
                "wow",
                true,
                status,
                null,
                null,
                slaRemaining,
                null,
                null));
    }

    private static PrMetadata openPr(PrTrackingRecord record) {
        return openPrWithReviews(record, List.of());
    }

    private static PrMetadata openPrWithReviews(PrTrackingRecord record, List<Review> reviews) {
        return new PrMetadata(
                RepoCoord.github(record.repo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.OPEN,
                null,
                List.of(),
                reviews);
    }

    private static PrMetadata openPrWithRequestedReviewers(
            PrTrackingRecord record, List<String> reviewers, List<Review> reviews) {
        return new PrMetadata(
                RepoCoord.github(record.repo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.OPEN,
                null,
                reviewers,
                reviews);
    }

    /**
     * A PR whose requested-team membership resolution failed (null, not a partial list) — the source
     * client's signal that the requested-team review fallback is unresolved for this poll.
     */
    private static PrMetadata openPrWithUnresolvedRequestedReviewers(PrTrackingRecord record, List<Review> reviews) {
        return new PrMetadata(
                RepoCoord.github(record.repo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.OPEN,
                null,
                null,
                reviews,
                null,
                null,
                List.of());
    }

    private static PrMetadata openMergeablePr(PrTrackingRecord record) {
        return openMergeablePrWithReviews(record, List.of());
    }

    private static PrMetadata openMergeablePrWithReviews(PrTrackingRecord record, List<Review> reviews) {
        return new PrMetadata(
                RepoCoord.github(record.repo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.OPEN,
                true,
                List.of(),
                reviews);
    }

    private static PrMetadata closedPr(PrTrackingRecord record) {
        return new PrMetadata(
                RepoCoord.github(record.repo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.CLOSED,
                null,
                List.of(),
                List.of());
    }

    private static PrMetadata mergedPr(PrTrackingRecord record) {
        return new PrMetadata(
                RepoCoord.github(record.repo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.MERGED,
                null,
                List.of(),
                List.of());
    }

    private static Review review(Review.ReviewState state) {
        return new Review("reviewer", state, Instant.now());
    }

    private static Review review(Review.ReviewState state, Instant submittedAt) {
        return new Review("reviewer", state, submittedAt);
    }

    private static Ticket ticket(long ticketId) {
        return Ticket.builder()
                .id(new TicketId(ticketId))
                .channelId("C_SUPPORT")
                .queryTs(MessageTs.of("1700000000.000001"))
                .status(TicketStatus.opened)
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .lastInteractedAt(Instant.now())
                .build();
    }
}
