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
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
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
import java.time.Instant;
import java.util.List;
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
        when(gitHubClient.getPullRequest(first.githubRepo(), first.prNumber()))
                .thenReturn(new GitHubPullRequest(
                        first.githubRepo(), first.prNumber(), first.prCreatedAt(), GitHubPullRequest.PrState.CLOSED));
        when(ticketRepository.findTicketById(new TicketId(first.ticketId())))
                .thenThrow(new RuntimeException("database temporarily unavailable"));
        when(gitHubClient.getPullRequest(second.githubRepo(), second.prNumber()))
                .thenReturn(new GitHubPullRequest(
                        second.githubRepo(), second.prNumber(), second.prCreatedAt(), GitHubPullRequest.PrState.OPEN));

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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.CLOSED));
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.CLOSED));
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.CLOSED));
        when(ticketRepository.findTicketById(new TicketId(record.ticketId()))).thenReturn(ticket(100L));
        when(prTrackingRepository.hasAnyActiveClosableForTicket(record.ticketId()))
                .thenReturn(true);

        // when
        poller.poll();

        // then
        verify(ticketProcessingService, never()).closeForPrResolution(any(), any(), any());
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(), record.prNumber(), record.prCreatedAt(), GitHubPullRequest.PrState.OPEN));

        // when
        poller.poll();

        // then
        verifyNoInteractions(escalationProcessingService);
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.CLOSED));
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(), record.prNumber(), record.prCreatedAt(), GitHubPullRequest.PrState.OPEN));
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
                null);
        when(prTrackingRepository.findAllActive()).thenReturn(List.of(record));
        when(gitHubClient.getPullRequest(record.githubRepo(), record.prNumber()))
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.CLOSED));
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.MERGED));
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
                .thenReturn(new GitHubPullRequest(
                        record.githubRepo(),
                        record.prNumber(),
                        record.prCreatedAt(),
                        GitHubPullRequest.PrState.CLOSED));
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

    private PrLifecyclePoller createPoller() {
        return new PrLifecyclePoller(
                prTrackingRepository,
                gitHubClient,
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
                null);
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
