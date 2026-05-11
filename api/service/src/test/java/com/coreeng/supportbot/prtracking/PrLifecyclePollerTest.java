package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
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
    private PrMessageRenderer messageRenderer;

    @Mock
    private EscalationTeamsRegistry escalationTeamsRegistry;

    @BeforeEach
    void setUp() {
        lenient().when(prSourceClients.forProvider(Provider.GITHUB)).thenReturn(prSourceClient);
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(first.githubRepo()), first.prNumber()))
                .thenReturn(closedPr(first));
        when(ticketRepository.findTicketById(new TicketId(first.ticketId())))
                .thenThrow(new RuntimeException("database temporarily unavailable"));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(second.githubRepo()), second.prNumber()))
                .thenReturn(openPr(second));

        // when / then
        assertDoesNotThrow(poller::poll);
        verify(prSourceClient).fetchPullRequest(RepoCoord.github(first.githubRepo()), first.prNumber());
        verify(prSourceClient).fetchPullRequest(RepoCoord.github(second.githubRepo()), second.prNumber());
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        PrTrackingRecord record = new PrTrackingRecord(
                1L,
                100L,
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
                null);
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
        when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            PrTrackingRecord record = new PrTrackingRecord(
                    1L,
                    100L,
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
                    null);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            PrTrackingRecord ancientNoSlaRecord = new PrTrackingRecord(
                    1L,
                    100L,
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
                    null);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(ancientNoSlaRecord));
            when(prSourceClient.fetchPullRequest(
                            RepoCoord.github(ancientNoSlaRecord.githubRepo()), ancientNoSlaRecord.prNumber()))
                    .thenReturn(openPr(ancientNoSlaRecord));

            poller.poll();

            verify(prTrackingRepository, never()).updateStatus(anyLong(), eq(PrTrackingStatus.ESCALATED), any(), any());
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void changesRequestedTransitionForNoSlaPr() {
            // given — OPEN record with null slaDeadline but a CHANGES_REQUESTED review present
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = new PrTrackingRecord(
                    1L,
                    100L,
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
                    null);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
                            .formatted(record.githubRepo(), record.prNumber()));
        }

        @Test
        void approvedTransitionForNoSlaUpdatesStatusWithoutPausingSla() {
            // given — OPEN no-SLA record (null slaDeadline) with an APPROVED review present
            PrLifecyclePoller poller = createPoller();
            PrTrackingRecord record = new PrTrackingRecord(
                    1L,
                    100L,
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
                    null);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
                    .thenReturn(openPrWithReviews(record, List.of(review(Review.ReviewState.APPROVED))));
            when(prTrackingProps.repositories()).thenReturn(List.of());

            // when
            poller.poll();

            // then — no teams requested, all reviews accepted
            verify(prTrackingRepository).pauseSla(eq(record.id()), eq(PrTrackingStatus.APPROVED), any(Duration.class));
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            PrTrackingRecord record = new PrTrackingRecord(
                    1L,
                    100L,
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
                    null);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            PrTrackingRecord record = new PrTrackingRecord(
                    1L,
                    100L,
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
                    null);
            when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
            when(prSourceClient.fetchPullRequest(RepoCoord.github(record.githubRepo()), record.prNumber()))
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
                messageRenderer,
                escalationTeamsRegistry);
    }

    private static PrTrackingRecord record(
            long id, long ticketId, String repo, int prNumber, PrTrackingStatus status, Instant slaDeadline) {
        return new PrTrackingRecord(
                id,
                ticketId,
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
                null);
    }

    private static PrTrackingRecord pausedRecord(
            long id, long ticketId, String repo, int prNumber, PrTrackingStatus status, Duration slaRemaining) {
        return new PrTrackingRecord(
                id,
                ticketId,
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
                null);
    }

    private static PrMetadata openPr(PrTrackingRecord record) {
        return openPrWithReviews(record, List.of());
    }

    private static PrMetadata openPrWithReviews(PrTrackingRecord record, List<Review> reviews) {
        return new PrMetadata(
                RepoCoord.github(record.githubRepo()),
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
                RepoCoord.github(record.githubRepo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.OPEN,
                null,
                reviewers,
                reviews);
    }

    private static PrMetadata openMergeablePr(PrTrackingRecord record) {
        return openMergeablePrWithReviews(record, List.of());
    }

    private static PrMetadata openMergeablePrWithReviews(PrTrackingRecord record, List<Review> reviews) {
        return new PrMetadata(
                RepoCoord.github(record.githubRepo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.OPEN,
                true,
                List.of(),
                reviews);
    }

    private static PrMetadata closedPr(PrTrackingRecord record) {
        return new PrMetadata(
                RepoCoord.github(record.githubRepo()),
                record.prNumber(),
                record.prCreatedAt(),
                PrMetadata.PrState.CLOSED,
                null,
                List.of(),
                List.of());
    }

    private static PrMetadata mergedPr(PrTrackingRecord record) {
        return new PrMetadata(
                RepoCoord.github(record.githubRepo()),
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
