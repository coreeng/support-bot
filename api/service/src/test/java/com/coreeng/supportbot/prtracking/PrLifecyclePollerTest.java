package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.github.GitHubPullRequestReview;
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
    private GitHubClient gitHubClient;

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
        when(gitHubClient.getPullRequest(first.githubRepo(), first.prNumber())).thenReturn(closedPr(first));
        when(ticketRepository.findTicketById(new TicketId(first.ticketId())))
                .thenThrow(new RuntimeException("database temporarily unavailable"));
        when(gitHubClient.getPullRequest(second.githubRepo(), second.prNumber()))
                .thenReturn(openPr(second));

        // when / then
        assertDoesNotThrow(poller::poll);
        verify(gitHubClient).getPullRequest(first.githubRepo(), first.prNumber());
        verify(gitHubClient).getPullRequest(second.githubRepo(), second.prNumber());
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPr(record));

            // when
            poller.poll();

            // then — no crash, no state changes
            verify(prTrackingRepository, never()).updateStatus(anyLong(), any(), any(), any());
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));
            when(ticketRepository.findTicketById(new TicketId(record.ticketId())))
                    .thenReturn(ticket(100L));

            // when
            poller.poll();

            // then
            verify(prTrackingRepository).updateStatus(eq(record.id()), eq(PrTrackingStatus.CHANGES_REQUESTED), isNull(), eq(record.escalationId()));
            ArgumentCaptor<SlackPostMessageRequest> captor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
            verify(slackClient).postMessage(captor.capture());
            assertThat(captor.getValue().message().getText()).contains(
                    "PR `%s#%d` has been reviewed and changes have been requested."
                    .formatted(record.githubRepo(), record.prNumber()));
        }

        @Test
        void skipsApprovedTransitionWhenSlaDeadlineIsNull() {
            // given — OPEN record with null slaDeadline but an APPROVED review present
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));

            // when
            poller.poll();

            // then — transition is skipped; no state change, no Slack notification
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(prTrackingRepository).updateStatus(eq(1L), eq(PrTrackingStatus.APPROVED), any(), any());
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.COMMENTED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(
                                    review(
                                            GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(3600)),
                                    review(
                                            GitHubPullRequestReview.ReviewState.APPROVED,
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.DISMISSED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openMergeablePrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(
                                    // Non-team member approves, team member requests changes
                                    new GitHubPullRequestReview(
                                            "outsider",
                                            GitHubPullRequestReview.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(60)),
                                    new GitHubPullRequestReview(
                                            "team-member",
                                            GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(30)))));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            "platform-team",
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
            when(gitHubClient.resolveTeamReviewers("my-org", "platform-team")).thenReturn(List.of("team-member"));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            verify(gitHubClient, never()).resolveTeamReviewers(any(), any());
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            "platform-team",
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
            when(gitHubClient.resolveTeamReviewers("my-org", "platform-team"))
                    .thenThrow(new GitHubApiException(403, "forbidden"));

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
            when(gitHubClient.getPullRequest(any(), eq(11))).thenReturn(openPr(first));
            when(gitHubClient.getPullRequest(any(), eq(22))).thenReturn(openPr(second));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            "my-org/repo-a",
                            "wow",
                            "platform-team",
                            List.of(),
                            new PrTrackingProps.Sla(null, Duration.ofDays(2), null))));
            when(gitHubClient.resolveTeamReviewers("my-org", "platform-team")).thenReturn(List.of("team-member"));

            // when
            poller.poll();

            // then — team members fetched only once
            verify(gitHubClient, org.mockito.Mockito.times(1)).resolveTeamReviewers("my-org", "platform-team");
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithRequestedReviewers(
                            record,
                            List.of("auto-reviewer"),
                            List.of(
                                    new GitHubPullRequestReview(
                                            "outsider",
                                            GitHubPullRequestReview.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(60)),
                                    new GitHubPullRequestReview(
                                            "auto-reviewer",
                                            GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED,
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.APPROVED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(
                                    review(
                                            GitHubPullRequestReview.ReviewState.COMMENTED,
                                            reviewTime.minusSeconds(3600)),
                                    review(GitHubPullRequestReview.ReviewState.APPROVED, reviewTime))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record,
                            List.of(review(
                                    GitHubPullRequestReview.ReviewState.COMMENTED,
                                    existingReviewTime.minusSeconds(60)))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(
                            openPrWithReviews(record, List.of(review(GitHubPullRequestReview.ReviewState.DISMISSED))));

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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));
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
            when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                    .thenReturn(openPrWithReviews(
                            record, List.of(review(GitHubPullRequestReview.ReviewState.CHANGES_REQUESTED))));
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
                gitHubClient,
                new TeamReviewFilter(gitHubClient),
                ticketRepository,
                ticketProcessingService,
                escalationProcessingService,
                ticketSlackService,
                slackClient,
                prTrackingProps);
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

    private static GitHubPullRequest openPr(PrTrackingRecord record) {
        return openPrWithReviews(record, List.of());
    }

    private static GitHubPullRequest openPrWithReviews(PrTrackingRecord record, List<GitHubPullRequestReview> reviews) {
        return new GitHubPullRequest(
                record.githubRepo(),
                record.prNumber(),
                record.prCreatedAt(),
                GitHubPullRequest.PrState.OPEN,
                null,
                null,
                List.of(),
                reviews);
    }

    private static GitHubPullRequest openPrWithRequestedReviewers(
            PrTrackingRecord record, List<String> reviewers, List<GitHubPullRequestReview> reviews) {
        return new GitHubPullRequest(
                record.githubRepo(),
                record.prNumber(),
                record.prCreatedAt(),
                GitHubPullRequest.PrState.OPEN,
                null,
                null,
                reviewers,
                reviews);
    }

    private static GitHubPullRequest openMergeablePr(PrTrackingRecord record) {
        return openMergeablePrWithReviews(record, List.of());
    }

    private static GitHubPullRequest openMergeablePrWithReviews(
            PrTrackingRecord record, List<GitHubPullRequestReview> reviews) {
        return new GitHubPullRequest(
                record.githubRepo(),
                record.prNumber(),
                record.prCreatedAt(),
                GitHubPullRequest.PrState.OPEN,
                true,
                "clean",
                List.of(),
                reviews);
    }

    private static GitHubPullRequest closedPr(PrTrackingRecord record) {
        return new GitHubPullRequest(
                record.githubRepo(),
                record.prNumber(),
                record.prCreatedAt(),
                GitHubPullRequest.PrState.CLOSED,
                null,
                null,
                List.of(),
                List.of());
    }

    private static GitHubPullRequest mergedPr(PrTrackingRecord record) {
        return new GitHubPullRequest(
                record.githubRepo(),
                record.prNumber(),
                record.prCreatedAt(),
                GitHubPullRequest.PrState.MERGED,
                null,
                null,
                List.of(),
                List.of());
    }

    private static GitHubPullRequestReview review(GitHubPullRequestReview.ReviewState state) {
        return new GitHubPullRequestReview("reviewer", state, Instant.now());
    }

    private static GitHubPullRequestReview review(GitHubPullRequestReview.ReviewState state, Instant submittedAt) {
        return new GitHubPullRequestReview("reviewer", state, submittedAt);
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
