package com.coreeng.supportbot;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.escalation.EscalationInMemoryRepository;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.prtracking.PrDetectionOutcome;
import com.coreeng.supportbot.prtracking.PrDetectionService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketSubmission;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class TicketProcessingServiceTests {
    private static final MessageTs MESSAGE_TS = MessageTs.of("some-message-ts");
    private static final String USER_ID = "some-user-id";

    private TicketProcessingService ticketProcessingService;
    private TicketRepository ticketRepository;

    @Mock
    private TicketSlackService slackService;

    @Mock
    private PrDetectionService prDetectionService;

    private SlackTicketsProps slackTicketsProps;
    private TicketAssignmentProps assignmentProps;

    @Mock
    private ApplicationEventPublisher publisher;

    @Captor
    private ArgumentCaptor<TicketCreatedMessage> createdMessageCaptor;

    @BeforeEach
    public void setUp() {
        ZoneId timezone = ZoneId.of("UTC");
        EscalationQueryService escalationQueryService =
                new EscalationQueryService(new EscalationInMemoryRepository(timezone));
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, timezone);
        slackTicketsProps = new SlackTicketsProps("some-channel-id", "eyes", "ticket", "white_check_mark", "rocket");
        assignmentProps = new TicketAssignmentProps(true, new TicketAssignmentProps.Encryption(false, null));
        ticketProcessingService = new TicketProcessingService(
                ticketRepository,
                slackService,
                escalationQueryService,
                slackTicketsProps,
                assignmentProps,
                publisher,
                Optional.empty());
    }

    @Test
    public void shouldCreateQueryOnMessage() {
        // when
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        ticketProcessingService.handleMessagePosted(new MessagePosted("some message", USER_ID, threadRef));

        // then
        assertTrue(ticketRepository.queryExists(threadRef), "Query is created");
    }

    @Test
    public void shouldIgnoreMessageToDifferentChannel() {
        // when
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, "some-random-channnel");
        ticketProcessingService.handleMessagePosted(new MessagePosted("some message", USER_ID, threadRef));

        // then
        assertFalse(ticketRepository.queryExists(threadRef), "Event is ignored");
    }

    @Test
    public void shouldIgnoreMessageInThreads() {
        // when
        MessageRef threadRef = new MessageRef(MESSAGE_TS, MessageTs.of("thread-ts"), slackTicketsProps.channelId());
        ticketProcessingService.handleMessagePosted(new MessagePosted("some message", USER_ID, threadRef));

        // then
        assertFalse(ticketRepository.queryExists(threadRef), "Event is ignored");
    }

    @Test
    public void shouldCreateTicketOnEyes() {
        // given
        MessageTs postedMessageTs = MessageTs.of("posted-message-ts");

        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        MessageRef ticketFormRef = new MessageRef(postedMessageTs, MESSAGE_TS, slackTicketsProps.channelId());
        when(slackService.postTicketForm(eq(threadRef), createdMessageCaptor.capture()))
                .thenReturn(ticketFormRef);

        // when
        ticketProcessingService.handleReactionAdded(
                new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, threadRef));

        // then
        assertTrue(ticketRepository.queryExists(threadRef), "Query is created");

        Ticket ticket = ticketRepository.findTicketByQuery(threadRef);
        assertNotNull(ticket, "Ticket is created");
        assertNotNull(ticket.id());
        assertEquals(TicketStatus.opened, ticket.status());
        assertEquals(MESSAGE_TS, ticket.queryTs());

        verify(slackService, description("Post is tracked")).markPostTracked(threadRef);
        verify(slackService, description("Ticket form is posted"))
                .postTicketForm(eq(threadRef), createdMessageCaptor.capture());
    }

    @Test
    public void shouldAssignFirstReactorWhenEnabledAndPreserveOnSubmit() {
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        when(slackService.postTicketForm(eq(threadRef), any()))
                .thenReturn(new MessageRef(MessageTs.of("form"), MESSAGE_TS, slackTicketsProps.channelId()));

        ticketProcessingService.handleReactionAdded(
                new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, threadRef));

        Ticket ticket = ticketRepository.findTicketByQuery(threadRef);
        assertNotNull(ticket);
        assertEquals(SlackId.user(USER_ID), ticket.assignedTo());
        TicketId localTicketId = requireNonNull(ticket.id());

        TicketSubmission submission = TicketSubmission.builder()
                .ticketId(localTicketId)
                .status(TicketStatus.opened)
                .authorsTeam(new TicketTeam.KnownTeam("platform"))
                .tags(ImmutableList.of("tag1"))
                .impact("low")
                .confirmed(true)
                .build();

        ticketProcessingService.submit(submission);

        Ticket afterSubmit = ticketRepository.findTicketById(localTicketId);
        assertNotNull(afterSubmit);
        assertEquals(
                SlackId.user(USER_ID),
                afterSubmit.assignedTo(),
                "Assignment is preserved when update uses assignedTo(null)");
    }

    @Test
    public void shouldNotAssignWhenFeatureDisabled() {
        assignmentProps = new TicketAssignmentProps(false, new TicketAssignmentProps.Encryption(false, null));
        ZoneId timezone = ZoneId.of("UTC");
        EscalationQueryService escalationQueryService =
                new EscalationQueryService(new EscalationInMemoryRepository(timezone));
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, timezone);
        ticketProcessingService = new TicketProcessingService(
                ticketRepository,
                slackService,
                escalationQueryService,
                slackTicketsProps,
                assignmentProps,
                publisher,
                Optional.empty());
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        when(slackService.postTicketForm(eq(threadRef), any()))
                .thenReturn(new MessageRef(MessageTs.of("form"), MESSAGE_TS, slackTicketsProps.channelId()));

        ticketProcessingService.handleReactionAdded(
                new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, threadRef));

        Ticket ticket = ticketRepository.findTicketByQuery(threadRef);
        assertNotNull(ticket);
        assertNull(ticket.assignedTo(), "Assignment is skipped when feature disabled");
    }

    @Test
    public void shouldAutoCreateTicketAndNotifyPrDetectionWhenPrLinkDetected() {
        // given
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, slackTicketsProps.channelId());

        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleMessagePosted(any(), any())).thenReturn(PrDetectionOutcome.tracked());
        when(slackService.postTicketForm(eq(new MessageRef(MESSAGE_TS, slackTicketsProps.channelId())), any()))
                .thenReturn(formRef);

        // when
        service.handleMessagePosted(new MessagePosted("https://github.com/org/repo/pull/1", USER_ID, queryRef));

        // then
        assertTrue(ticketRepository.queryExists(queryRef), "query is created");
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket, "ticket is auto-created for PR link");
        assertEquals(TicketStatus.opened, ticket.status());

        verify(slackService).postTicketForm(eq(new MessageRef(MESSAGE_TS, slackTicketsProps.channelId())), any());

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(prDetectionService).handleMessagePosted(any(MessagePosted.class), ticketCaptor.capture());
        assertEquals(ticket.id(), ticketCaptor.getValue().id());
    }

    @Test
    public void shouldNotAutoCreateTicketWhenNoPrLinksInMessage() {
        // given
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());

        when(prDetectionService.containsPrLinks(any())).thenReturn(false);

        // when
        service.handleMessagePosted(new MessagePosted("just a question", USER_ID, queryRef));

        // then
        assertTrue(ticketRepository.queryExists(queryRef), "query is still created");
        assertNull(ticketRepository.findTicketByQuery(queryRef), "no ticket created without PR link");
        verify(prDetectionService, never()).handleMessagePosted(any(), any());
    }

    @Test
    public void shouldCallPrDetectionOnThreadReplyForExistingTicket() {
        // given — create a ticket first via reaction
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, slackTicketsProps.channelId());

        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        when(prDetectionService.handleMessagePosted(any(), any())).thenReturn(PrDetectionOutcome.tracked());
        service.handleReactionAdded(new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket);
        TicketId ticketId = requireNonNull(ticket.id());

        // when — a thread reply arrives
        MessageRef replyRef = new MessageRef(MessageTs.of("reply-ts"), MESSAGE_TS, slackTicketsProps.channelId());
        service.handleMessagePosted(new MessagePosted("a PR follow-up", USER_ID, replyRef));

        // then
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(prDetectionService).handleMessagePosted(any(MessagePosted.class), ticketCaptor.capture());
        assertEquals(ticketId, ticketCaptor.getValue().id());
    }

    @Test
    public void shouldCloseTicketWhenPrDetectionSignalsAlreadyMerged() {
        // given — PR link in the opening message, GitHub says the PR is already merged
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, slackTicketsProps.channelId());

        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleMessagePosted(any(), any()))
                .thenReturn(PrDetectionOutcome.notOpen(ImmutableList.of("pr-review"), "low"));
        when(slackService.postTicketForm(eq(new MessageRef(MESSAGE_TS, slackTicketsProps.channelId())), any()))
                .thenReturn(formRef);

        // when
        service.handleMessagePosted(new MessagePosted("https://github.com/org/repo/pull/1", USER_ID, queryRef));

        // then — ticket is auto-created and immediately closed with the tags/impact from the outcome
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket, "ticket is created");
        assertEquals(TicketStatus.closed, ticket.status());
        assertEquals(ImmutableList.of("pr-review"), ticket.tags());
        assertEquals("low", ticket.impact());
    }

    @Test
    public void shouldCloseTicketWhenPrDetectionSignalsAlreadyMergedOnThreadReply() {
        // given — ticket already exists, a thread reply arrives with a merged-PR link
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, slackTicketsProps.channelId());

        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        when(prDetectionService.handleMessagePosted(any(), any()))
                .thenReturn(PrDetectionOutcome.notOpen(ImmutableList.of("pr-review"), "medium"));

        service.handleReactionAdded(new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket);

        // when — a thread reply is posted with a merged PR link
        MessageRef replyRef = new MessageRef(MessageTs.of("reply-ts"), MESSAGE_TS, slackTicketsProps.channelId());
        service.handleMessagePosted(new MessagePosted("merged PR link", USER_ID, replyRef));

        // then — ticket is closed with tags/impact from the outcome
        Ticket updated = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(updated);
        assertEquals(TicketStatus.closed, updated.status());
        assertEquals(ImmutableList.of("pr-review"), updated.tags());
        assertEquals("medium", updated.impact());
    }

    @Test
    public void shouldCloseTicketWithGivenTagsAndImpact() {
        // given
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        // when
        ticketProcessingService.closeForPrResolution(ticketId, ImmutableList.of("pr-review", "infra"), "medium");

        // then
        Ticket closed = ticketRepository.findTicketById(ticketId);
        assertNotNull(closed);
        assertEquals(TicketStatus.closed, closed.status());
        assertEquals(ImmutableList.of("pr-review", "infra"), closed.tags());
        assertEquals("medium", closed.impact());
        verify(slackService).markTicketClosed(any());
    }

    @Test
    public void shouldSkipCloseForPrResolutionGracefullyWhenTicketNotFound() {
        // when / then
        assertDoesNotThrow(
                () -> ticketProcessingService.closeForPrResolution(new TicketId(999L), ImmutableList.of("tag"), "low"));
    }

    @Test
    public void shouldNotChangeStatusWhenTicketAlreadyClosed() {
        // given — create and close the ticket once
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());
        ticketProcessingService.closeForPrResolution(ticketId, ImmutableList.of("original-tag"), "low");

        // when — attempt to close again with different values
        ticketProcessingService.closeForPrResolution(ticketId, ImmutableList.of("different-tag"), "high");

        // then — tags from the first close are preserved
        Ticket afterSecondClose = ticketRepository.findTicketById(ticketId);
        assertNotNull(afterSecondClose);
        assertEquals(TicketStatus.closed, afterSecondClose.status());
        assertEquals(ImmutableList.of("original-tag"), afterSecondClose.tags());
        assertEquals("low", afterSecondClose.impact());
    }

    private TicketProcessingService serviceWithPrDetection() {
        return new TicketProcessingService(
                ticketRepository,
                slackService,
                new EscalationQueryService(new EscalationInMemoryRepository(ZoneId.of("UTC"))),
                slackTicketsProps,
                assignmentProps,
                publisher,
                Optional.of(prDetectionService));
    }

    private Ticket createTrackedTicket() {
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, slackTicketsProps.channelId());
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, slackTicketsProps.channelId());
        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        ticketProcessingService.handleReactionAdded(
                new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        return requireNonNull(ticketRepository.findTicketByQuery(queryRef));
    }
}
