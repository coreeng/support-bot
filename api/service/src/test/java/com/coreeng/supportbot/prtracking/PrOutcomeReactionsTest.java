package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrOutcomeReactionsTest {

    private static final String PR_EMOJI = "pr";
    private static final String MERGED_EMOJI = "pr-merged";
    private static final String CLOSED_EMOJI = "pr-closed";
    private static final long TICKET_ID = 100L;
    private static final MessageRef QUERY_REF = new MessageRef(MessageTs.of("1700000000.000001"), "C123");

    @Mock
    private PrTrackingProps prTrackingProps;

    @Mock
    private PrTrackingRepository prTrackingRepository;

    @Mock
    private TicketSlackService ticketSlackService;

    private PrOutcomeReactions reactions;

    @BeforeEach
    void setUp() {
        reactions = new PrOutcomeReactions(prTrackingProps, prTrackingRepository, ticketSlackService);
        lenient().when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
    }

    // ── onPrResolved ──

    @Test
    void swapsPrEmojiForMergedEmojiWhenLastPrIsMerged() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.MERGED);

        // then — the outcome emoji lands before the open marker (and any stale other outcome) is taken away
        InOrder inOrder = inOrder(ticketSlackService);
        inOrder.verify(ticketSlackService).addReaction(QUERY_REF, MERGED_EMOJI);
        inOrder.verify(ticketSlackService).removeReaction(QUERY_REF, PR_EMOJI);
        inOrder.verify(ticketSlackService).removeReaction(QUERY_REF, CLOSED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void treatsApprovedAndReadyToMergeAsMerged() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.APPROVED);

        // then
        verify(ticketSlackService).addReaction(QUERY_REF, MERGED_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, PR_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, CLOSED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void swapsPrEmojiForClosedEmojiWhenTheOnlyPrIsClosedWithoutMerge() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(1);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.CLOSED);

        // then
        verify(ticketSlackService).addReaction(QUERY_REF, CLOSED_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, PR_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, MERGED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void usesMergedEmojiForTicketsWithSeveralPrsEvenWhenTheLastIsClosed() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(2);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.CLOSED);

        // then
        verify(ticketSlackService).addReaction(QUERY_REF, MERGED_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, PR_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, CLOSED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void removesOnlyPrEmojiWhenJustOneOutcomeEmojiIsConfigured() {
        // given
        configure(MERGED_EMOJI, null);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.MERGED);

        // then
        verify(ticketSlackService).addReaction(QUERY_REF, MERGED_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, PR_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void doesNotRemoveTheSameEmojiTwiceWhenBothOutcomesShareIt() {
        // given
        configure(MERGED_EMOJI, MERGED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.MERGED);

        // then
        verify(ticketSlackService).addReaction(QUERY_REF, MERGED_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, PR_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void keepsPrEmojiWhileOtherPrsOnTheTicketAreStillActive() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(true);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.MERGED);

        // then
        verifyNoInteractions(ticketSlackService);
    }

    @Test
    void doesNothingWhenNoOutcomeEmojisAreConfigured() {
        // given
        configure(null, null);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.MERGED);

        // then — no lookup, no Slack calls: the open-PR emoji stays exactly as before
        verifyNoInteractions(ticketSlackService, prTrackingRepository);
    }

    @Test
    void keepsPrEmojiWhenOnlyMergedIsConfiguredAndTheOnlyPrClosed() {
        // given
        configure(MERGED_EMOJI, null);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(1);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.CLOSED);

        // then
        verifyNoInteractions(ticketSlackService);
    }

    @Test
    void keepsPrEmojiWhenOnlyClosedIsConfiguredAndThePrMerged() {
        // given
        configure(null, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.MERGED);

        // then
        verifyNoInteractions(ticketSlackService);
    }

    @Test
    void keepsPrEmojiWhenOnlyClosedIsConfiguredAndTheTicketHadSeveralPrs() {
        // given — several PRs always resolve to merged, which isn't configured
        configure(null, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(2);

        // when
        reactions.onPrResolved(ticket(), MessageEvent.CLOSED);

        // then
        verifyNoInteractions(ticketSlackService);
    }

    @Test
    void keepsPrEmojiWhenAddingTheOutcomeEmojiFails() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);
        doThrow(new IllegalStateException("invalid_name"))
                .when(ticketSlackService)
                .addReaction(QUERY_REF, MERGED_EMOJI);

        // when / then — no exception, and the open marker is not removed (better one stale emoji than none)
        assertThatCode(() -> reactions.onPrResolved(ticket(), MessageEvent.MERGED))
                .doesNotThrowAnyException();
        verify(ticketSlackService, never()).removeReaction(any(), any());
    }

    @Test
    void swallowsFailureToRemovePrEmoji() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenReturn(false);
        doThrow(new IllegalStateException("ratelimited"))
                .when(ticketSlackService)
                .removeReaction(QUERY_REF, PR_EMOJI);

        // when / then
        assertThatCode(() -> reactions.onPrResolved(ticket(), MessageEvent.MERGED))
                .doesNotThrowAnyException();
        verify(ticketSlackService).addReaction(QUERY_REF, MERGED_EMOJI);
    }

    @Test
    void neverThrowsForATicketWithoutAnId() {
        // given — the poller calls onPrResolved from a finally block, so it must not throw and mask an error
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        Ticket ticketWithoutId = ticket().toBuilder().id(null).build();

        // when / then
        assertThatCode(() -> reactions.onPrResolved(ticketWithoutId, MessageEvent.MERGED))
                .doesNotThrowAnyException();
        assertThatCode(() -> reactions.onPrTracked(ticketWithoutId)).doesNotThrowAnyException();
        verifyNoInteractions(ticketSlackService, prTrackingRepository);
    }

    @Test
    void swallowsRepositoryFailureOnResolve() {
        // given — the caller (the poller's closure flow) must never see this
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.hasAnyActiveForTicket(TICKET_ID)).thenThrow(new IllegalStateException("db down"));

        // when / then
        assertThatCode(() -> reactions.onPrResolved(ticket(), MessageEvent.MERGED))
                .doesNotThrowAnyException();
        verifyNoInteractions(ticketSlackService);
    }

    // ── onPrTracked ──

    @Test
    void newlyTrackedPrClearsAllConfiguredOutcomeEmojis() {
        // given — the ticket already had an earlier PR
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(2);

        // when
        reactions.onPrTracked(ticket());

        // then — only the outcome emojis go; pr-emoji (just added by detection) is left alone
        verify(ticketSlackService).removeReaction(QUERY_REF, MERGED_EMOJI);
        verify(ticketSlackService).removeReaction(QUERY_REF, CLOSED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void newlyTrackedPrClearsOnlyTheConfiguredOutcomeEmoji() {
        // given
        configure(MERGED_EMOJI, null);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(2);

        // when
        reactions.onPrTracked(ticket());

        // then
        verify(ticketSlackService).removeReaction(QUERY_REF, MERGED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void newlyTrackedPrRemovesASharedOutcomeEmojiOnce() {
        // given
        configure(MERGED_EMOJI, MERGED_EMOJI);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(2);

        // when
        reactions.onPrTracked(ticket());

        // then
        verify(ticketSlackService).removeReaction(QUERY_REF, MERGED_EMOJI);
        verifyNoMoreInteractions(ticketSlackService);
    }

    @Test
    void firstPrOnATicketMakesNoSlackCalls() {
        // given — no earlier PR, so there can't be an outcome emoji to clear
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(1);

        // when
        reactions.onPrTracked(ticket());

        // then
        verifyNoInteractions(ticketSlackService);
    }

    @Test
    void newlyTrackedPrDoesNothingWithoutOutcomeEmojis() {
        // given
        configure(null, null);

        // when
        reactions.onPrTracked(ticket());

        // then — no lookup, no Slack calls
        verifyNoInteractions(ticketSlackService, prTrackingRepository);
    }

    @Test
    void newlyTrackedPrSwallowsRemovalFailure() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenReturn(2);
        doThrow(new IllegalStateException("ratelimited"))
                .when(ticketSlackService)
                .removeReaction(QUERY_REF, MERGED_EMOJI);

        // when / then — detection must never see this
        assertThatCode(() -> reactions.onPrTracked(ticket())).doesNotThrowAnyException();
    }

    @Test
    void newlyTrackedPrSwallowsRepositoryFailure() {
        // given
        configure(MERGED_EMOJI, CLOSED_EMOJI);
        when(prTrackingRepository.countForTicket(TICKET_ID)).thenThrow(new IllegalStateException("db down"));

        // when / then
        assertThatCode(() -> reactions.onPrTracked(ticket())).doesNotThrowAnyException();
        verifyNoInteractions(ticketSlackService);
    }

    private void configure(@Nullable String merged, @Nullable String closed) {
        when(prTrackingProps.outcomeEmojis()).thenReturn(new PrTrackingProps.OutcomeEmojis(merged, closed));
    }

    private static Ticket ticket() {
        return Ticket.builder()
                .id(new TicketId(TICKET_ID))
                .channelId(QUERY_REF.channelId())
                .queryTs(QUERY_REF.ts())
                .status(TicketStatus.opened)
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .lastInteractedAt(Instant.now())
                .build();
    }
}
