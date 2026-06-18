package com.coreeng.supportbot;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.config.SlackChannelProps;
import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.escalation.EscalationInMemoryRepository;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.prtracking.PrDetectionOutcome;
import com.coreeng.supportbot.prtracking.PrDetectionService;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.StalenessTagTarget;
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
    private static final String CHANNEL_ID = "some-channel-id";
    private static final String USER_ID = "some-user-id";
    private static final String SUPPORT_GROUP_ID = "S12345SUPPORT";

    private TicketProcessingService ticketProcessingService;
    private TicketRepository ticketRepository;
    private EscalationQueryService escalationQueryService;

    @Mock
    private TicketSlackService slackService;

    @Mock
    private PrDetectionService prDetectionService;

    @Mock
    private RbacService rbacService;

    private SlackTicketsProps slackTicketsProps;
    private TicketAssignmentProps assignmentProps;
    private SupportTeamProps supportTeamProps;

    @Mock
    private ApplicationEventPublisher publisher;

    @Captor
    private ArgumentCaptor<TicketCreatedMessage> createdMessageCaptor;

    @Captor
    private ArgumentCaptor<StalenessTagTarget> stalenessTagTargetCaptor;

    @BeforeEach
    public void setUp() {
        ZoneId timezone = ZoneId.of("UTC");
        escalationQueryService = new EscalationQueryService(new EscalationInMemoryRepository(timezone));
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, timezone);
        slackTicketsProps =
                new SlackTicketsProps(CHANNEL_ID, List.of(), "eyes", "ticket", "white_check_mark", "rocket");
        SlackChannelRegistry channelRegistry = new SlackChannelRegistry(slackTicketsProps);
        assignmentProps = new TicketAssignmentProps(true, new TicketAssignmentProps.Encryption(false, null));
        supportTeamProps = new SupportTeamProps("Core Support", "support", SUPPORT_GROUP_ID);
        ticketProcessingService = buildService(channelRegistry, Optional.empty());
    }

    /** Builds a registry with a single channel in the given track mode. */
    private SlackChannelRegistry registryFor(String channelId, TrackMode mode) {
        return new SlackChannelRegistry(new SlackTicketsProps(
                null,
                List.of(new SlackChannelProps(mode.name().toLowerCase(), channelId, mode)),
                "eyes",
                "ticket",
                "white_check_mark",
                "rocket"));
    }

    /** Builds a processing service against a specific channel registry / PR-detection setup. */
    private TicketProcessingService buildService(
            SlackChannelRegistry channelRegistry, Optional<PrDetectionService> prDetection) {
        return new TicketProcessingService(
                ticketRepository,
                slackService,
                escalationQueryService,
                slackTicketsProps,
                channelRegistry,
                assignmentProps,
                publisher,
                prDetection,
                rbacService,
                supportTeamProps);
    }

    @Test
    public void shouldCreateQueryOnMessage() {
        // when
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
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
    public void shouldCreateQueryInQueriesOnlyChannel() {
        // given a channel that tracks only normal queries
        SlackChannelRegistry registry = new SlackChannelRegistry(new SlackTicketsProps(
                null,
                List.of(new SlackChannelProps("queries-only", "Q123", TrackMode.QUERIES)),
                "eyes",
                "ticket",
                "white_check_mark",
                "rocket"));
        TicketProcessingService service = buildService(registry, Optional.empty());

        // when
        MessageRef ref = new MessageRef(MESSAGE_TS, null, "Q123");
        service.handleMessagePosted(new MessagePosted("some message", USER_ID, ref));

        // then
        assertTrue(ticketRepository.queryExists(ref), "Query is created in a QUERIES channel");
    }

    @Test
    public void shouldSuppressNormalQueryInPrsOnlyChannel() {
        // given a PRS-only channel with no PR detection wired (so no message carries a PR link)
        SlackChannelRegistry registry = new SlackChannelRegistry(new SlackTicketsProps(
                null,
                List.of(new SlackChannelProps("prs-only", "P123", TrackMode.PRS)),
                "eyes",
                "ticket",
                "white_check_mark",
                "rocket"));
        TicketProcessingService service = buildService(registry, Optional.empty());

        // when a plain (non-PR) message is posted
        MessageRef ref = new MessageRef(MESSAGE_TS, null, "P123");
        service.handleMessagePosted(new MessagePosted("just a question", USER_ID, ref));

        // then the normal query flow is suppressed
        assertFalse(ticketRepository.queryExists(ref), "Normal query is suppressed in a PRS-only channel");
    }

    @Test
    public void shouldMonitorMultipleChannels() {
        // given two channels both tracking everything
        SlackChannelRegistry registry = new SlackChannelRegistry(new SlackTicketsProps(
                null,
                List.of(
                        new SlackChannelProps("first", "C1", TrackMode.BOTH),
                        new SlackChannelProps("second", "C2", TrackMode.BOTH)),
                "eyes",
                "ticket",
                "white_check_mark",
                "rocket"));
        TicketProcessingService service = buildService(registry, Optional.empty());

        // when messages are posted to both channels
        MessageRef refOne = new MessageRef(MessageTs.of("ts-1"), null, "C1");
        MessageRef refTwo = new MessageRef(MessageTs.of("ts-2"), null, "C2");
        service.handleMessagePosted(new MessagePosted("first", USER_ID, refOne));
        service.handleMessagePosted(new MessagePosted("second", USER_ID, refTwo));

        // then both produce queries, aggregated into the same store
        assertTrue(ticketRepository.queryExists(refOne), "Query created in first channel");
        assertTrue(ticketRepository.queryExists(refTwo), "Query created in second channel");
    }

    @Test
    public void shouldCreateTicketAndRunPrDetectionForPrLinkInPrsOnlyChannel() {
        // given a PRS-only channel with PR detection wired
        TicketProcessingService service =
                buildService(registryFor("P123", TrackMode.PRS), Optional.of(prDetectionService));
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, "P123");
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, "P123");

        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleQueryMessagePosted(any(), any())).thenAnswer(invocation -> {
            Supplier<Ticket> ticketCreator = invocation.getArgument(1);
            ticketCreator.get();
            return PrDetectionOutcome.tracked();
        });
        when(slackService.postTicketForm(eq(new MessageRef(MESSAGE_TS, "P123")), any()))
                .thenReturn(formRef);

        // when a message carrying a PR link is posted
        service.handleMessagePosted(new MessagePosted("https://github.com/org/repo/pull/1", USER_ID, queryRef));

        // then the query + ticket are created even though this channel suppresses plain queries
        assertTrue(ticketRepository.queryExists(queryRef), "query is created for a PR link in a PRS-only channel");
        assertNotNull(ticketRepository.findTicketByQuery(queryRef), "ticket is auto-created for the PR link");
        verify(prDetectionService).handleQueryMessagePosted(any(MessagePosted.class), any());
    }

    @Test
    public void shouldSuppressReactionDrivenTicketInPrsOnlyChannel() {
        // given a PRS-only channel: reaction-driven creation is part of the normal query flow
        TicketProcessingService service = buildService(registryFor("P123", TrackMode.PRS), Optional.empty());
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, "P123");

        // when the initial reaction is added
        service.handleReactionAdded(
                new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, threadRef));

        // then nothing is created and no ticket form is posted
        assertFalse(
                ticketRepository.queryExists(threadRef), "reaction-driven query is suppressed in a PRS-only channel");
        verify(slackService, never()).postTicketForm(any(), any());
    }

    @Test
    public void shouldNotRunPrDetectionOnThreadReplyInQueriesOnlyChannel() {
        // given a QUERIES-only channel with a ticket already created via the normal reaction flow
        TicketProcessingService service =
                buildService(registryFor("Q123", TrackMode.QUERIES), Optional.of(prDetectionService));
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, "Q123");
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, "Q123");
        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        service.handleReactionAdded(new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        assertNotNull(ticketRepository.findTicketByQuery(queryRef), "precondition: ticket exists");

        // when a thread reply carrying a PR link arrives
        MessageRef replyRef = new MessageRef(MessageTs.of("reply-ts"), MESSAGE_TS, "Q123");
        service.handleMessagePosted(
                new MessagePosted("follow-up https://github.com/org/repo/pull/1", USER_ID, replyRef));

        // then PR detection is never consulted in a QUERIES-only channel
        verify(prDetectionService, never()).handleMessagePosted(any(), any());
    }

    @Test
    public void shouldIgnoreMessageInThreads() {
        // when
        MessageRef threadRef = new MessageRef(MESSAGE_TS, MessageTs.of("thread-ts"), CHANNEL_ID);
        ticketProcessingService.handleMessagePosted(new MessagePosted("some message", USER_ID, threadRef));

        // then
        assertFalse(ticketRepository.queryExists(threadRef), "Event is ignored");
    }

    @Test
    public void shouldCreateTicketOnEyes() {
        // given
        MessageTs postedMessageTs = MessageTs.of("posted-message-ts");

        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef ticketFormRef = new MessageRef(postedMessageTs, MESSAGE_TS, CHANNEL_ID);
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
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        when(slackService.postTicketForm(eq(threadRef), any()))
                .thenReturn(new MessageRef(MessageTs.of("form"), MESSAGE_TS, CHANNEL_ID));

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
        rebuildService(true);
        MessageRef threadRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        when(slackService.postTicketForm(eq(threadRef), any()))
                .thenReturn(new MessageRef(MessageTs.of("form"), MESSAGE_TS, CHANNEL_ID));

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
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, CHANNEL_ID);

        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleQueryMessagePosted(any(), any())).thenAnswer(invocation -> {
            Supplier<Ticket> ticketCreator = invocation.getArgument(1);
            Ticket created = ticketCreator.get();
            assertNotNull(created);
            return PrDetectionOutcome.tracked();
        });
        when(slackService.postTicketForm(eq(new MessageRef(MESSAGE_TS, CHANNEL_ID)), any()))
                .thenReturn(formRef);

        // when
        service.handleMessagePosted(new MessagePosted("https://github.com/org/repo/pull/1", USER_ID, queryRef));

        // then
        assertTrue(ticketRepository.queryExists(queryRef), "query is created");
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket, "ticket is auto-created for PR link");
        assertEquals(TicketStatus.opened, ticket.status());

        verify(slackService).postTicketForm(eq(new MessageRef(MESSAGE_TS, CHANNEL_ID)), any());
        verify(prDetectionService).handleQueryMessagePosted(any(MessagePosted.class), any());
    }

    @Test
    public void shouldNotAutoCreateTicketWhenNoPrLinksInMessage() {
        // given
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);

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
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, CHANNEL_ID);

        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        when(prDetectionService.handleMessagePosted(any(), any())).thenReturn(PrDetectionOutcome.tracked());
        service.handleReactionAdded(new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket);
        TicketId ticketId = requireNonNull(ticket.id());

        // when — a thread reply arrives
        MessageRef replyRef = new MessageRef(MessageTs.of("reply-ts"), MESSAGE_TS, CHANNEL_ID);
        service.handleMessagePosted(new MessagePosted("a PR follow-up", USER_ID, replyRef));

        // then
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(prDetectionService).handleMessagePosted(any(MessagePosted.class), ticketCaptor.capture());
        assertEquals(ticketId, ticketCaptor.getValue().id());
    }

    @Test
    public void shouldContinueWhenPrDetectionFailsOnQueryEvent() {
        // given
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleQueryMessagePosted(any(), any()))
                .thenThrow(new RuntimeException("pr subsystem down"));

        // when / then
        assertDoesNotThrow(() -> service.handleMessagePosted(
                new MessagePosted("https://github.com/org/repo/pull/1", USER_ID, queryRef)));
        assertTrue(ticketRepository.queryExists(queryRef));
        assertNull(ticketRepository.findTicketByQuery(queryRef));
    }

    @Test
    public void shouldContinueWhenPrDetectionFailsOnThreadReply() {
        // given
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, CHANNEL_ID);
        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        when(prDetectionService.handleMessagePosted(any(), any())).thenThrow(new RuntimeException("pr subsystem down"));

        service.handleReactionAdded(new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        Ticket ticket = requireNonNull(ticketRepository.findTicketByQuery(queryRef));
        TicketId ticketId = requireNonNull(ticket.id());
        service.markAsStale(ticketId);

        // when
        MessageRef replyRef = new MessageRef(MessageTs.of("reply-ts"), MESSAGE_TS, CHANNEL_ID);
        assertDoesNotThrow(() -> service.handleMessagePosted(new MessagePosted("reply", USER_ID, replyRef)));

        // then
        Ticket updated = ticketRepository.findTicketById(ticketId);
        assertNotNull(updated);
        assertEquals(TicketStatus.opened, updated.status());
    }

    @Test
    public void shouldCloseTicketWhenPrDetectionSignalsAlreadyMerged() {
        // given — PR link in the opening message, GitHub says the PR is already merged
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, CHANNEL_ID);

        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleQueryMessagePosted(any(), any())).thenAnswer(invocation -> {
            Supplier<Ticket> ticketCreator = invocation.getArgument(1);
            Ticket created = ticketCreator.get();
            assertNotNull(created);
            return new PrDetectionOutcome(true, ImmutableList.of("pr-review"), "low");
        });
        when(slackService.postTicketForm(eq(new MessageRef(MESSAGE_TS, CHANNEL_ID)), any()))
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
    public void shouldNotCreateTicketForClosedPrOnlyInOriginalMessage() {
        // given
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        when(prDetectionService.containsPrLinks(any())).thenReturn(true);
        when(prDetectionService.handleQueryMessagePosted(any(), any())).thenReturn(PrDetectionOutcome.skipped());

        // when
        service.handleMessagePosted(new MessagePosted("https://github.com/org/repo/pull/1", USER_ID, queryRef));

        // then
        assertTrue(ticketRepository.queryExists(queryRef), "query is created");
        assertNull(ticketRepository.findTicketByQuery(queryRef), "ticket is not created for closed-only PR links");
        verify(slackService, never()).postTicketForm(any(), any());
    }

    @Test
    public void shouldCloseTicketWhenPrDetectionSignalsAlreadyMergedOnThreadReply() {
        // given — ticket already exists, a thread reply arrives with a merged-PR link
        TicketProcessingService service = serviceWithPrDetection();
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, CHANNEL_ID);

        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        when(prDetectionService.handleMessagePosted(any(), any()))
                .thenReturn(new PrDetectionOutcome(true, ImmutableList.of("pr-review"), "medium"));

        service.handleReactionAdded(new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        Ticket ticket = ticketRepository.findTicketByQuery(queryRef);
        assertNotNull(ticket);

        // when — a thread reply is posted with a merged PR link
        MessageRef replyRef = new MessageRef(MessageTs.of("reply-ts"), MESSAGE_TS, CHANNEL_ID);
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

    @Test
    public void shouldTagAssigneeWhenMarkingStaleAndAssignmentEnabled() {
        // given
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        verify(slackService).warnStaleness(any(), stalenessTagTargetCaptor.capture());
        StalenessTagTarget target = stalenessTagTargetCaptor.getValue();
        assertInstanceOf(StalenessTagTarget.User.class, target);
        assertEquals(USER_ID, ((StalenessTagTarget.User) target).userId());
    }

    @Test
    public void shouldTagEyesReactorWhenAssignmentDisabled() {
        // given
        rebuildService(true);

        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        String supportEngineerId = "U_SUPPORT_ENG";
        when(slackService.getReactionUserIds(any(), eq("eyes"))).thenReturn(List.of(supportEngineerId));
        when(rbacService.isSupportBySlackId(SlackId.user(supportEngineerId))).thenReturn(true);

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsUser(supportEngineerId);
    }

    @Test
    public void shouldTagFirstSupportEngineerAmongMultipleEyesReactors() {
        // given
        rebuildService(true);

        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        String nonSupportUser = "U_CUSTOMER";
        String supportEngineerId = "U_SUPPORT_ENG";
        when(slackService.getReactionUserIds(any(), eq("eyes"))).thenReturn(List.of(nonSupportUser, supportEngineerId));
        when(rbacService.isSupportBySlackId(SlackId.user(nonSupportUser))).thenReturn(false);
        when(rbacService.isSupportBySlackId(SlackId.user(supportEngineerId))).thenReturn(true);

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsUser(supportEngineerId);
    }

    @Test
    public void shouldTagSquadWhenEyesReactorIsNotSupportEngineer() {
        // given
        rebuildService(true);

        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        String nonSupportUser = "U_CUSTOMER";
        when(slackService.getReactionUserIds(any(), eq("eyes"))).thenReturn(List.of(nonSupportUser));
        when(rbacService.isSupportBySlackId(SlackId.user(nonSupportUser))).thenReturn(false);

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsSquad();
    }

    @Test
    public void shouldTagSquadWhenNoEyesReaction() {
        // given
        rebuildService(true);

        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        when(slackService.getReactionUserIds(any(), eq("eyes"))).thenReturn(null);

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsSquad();
    }

    @Test
    public void shouldTagSquadWhenSlackApiFails() {
        // given
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        // Rebuild with assignment disabled so it falls through to eyes lookup
        rebuildService(false);

        when(slackService.getReactionUserIds(any(), eq("eyes")))
                .thenThrow(new SlackException(new RuntimeException("Slack API error")));

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsSquad();
    }

    @Test
    public void shouldResolveTargetWhenRemindingOfStaleTicket() {
        // given — create ticket and mark as stale
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());
        ticketProcessingService.markAsStale(ticketId);

        // when
        ticketProcessingService.remindOfStaleTicket(ticketId);

        // then — warnStaleness called twice (markAsStale + remind), both with assignee target
        verify(slackService, times(2)).warnStaleness(any(), stalenessTagTargetCaptor.capture());
        StalenessTagTarget target = stalenessTagTargetCaptor.getValue();
        assertInstanceOf(StalenessTagTarget.User.class, target);
        assertEquals(USER_ID, ((StalenessTagTarget.User) target).userId());
    }

    @Test
    public void shouldTagSquadWhenAssigneeIsNullDespiteAssignmentEnabled() {
        // given — create ticket without assignee via direct repo creation
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        ticketProcessingService.handleMessagePosted(new MessagePosted("some message", USER_ID, queryRef));

        Ticket newTicket = Ticket.createNew(MESSAGE_TS, CHANNEL_ID);
        newTicket = ticketRepository.createTicketIfNotExists(newTicket);
        TicketId ticketId = requireNonNull(newTicket.id());

        // Set createdMessageTs so onStatusUpdate can edit the form
        ticketRepository.updateTicket(
                newTicket.toBuilder().createdMessageTs(MessageTs.of("form-ts")).build());

        // assignee is null, so falls through to eyes lookup
        when(slackService.getReactionUserIds(any(), eq("eyes"))).thenReturn(null);

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsSquad();
    }

    @Test
    public void shouldTagSquadWhenEyesReactionUsersAreNull() {
        // given — eyes reaction exists but getUsers() returns null
        rebuildService(true);

        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        // getReactionUserIds returns null when the reaction's users list is null
        when(slackService.getReactionUserIds(any(), eq("eyes"))).thenReturn(null);

        // when
        ticketProcessingService.markAsStale(ticketId);

        // then
        assertStalenessTargetIsSquad();
    }

    private void rebuildService(boolean resetRepository) {
        assignmentProps = new TicketAssignmentProps(false, new TicketAssignmentProps.Encryption(false, null));
        ZoneId timezone = ZoneId.of("UTC");
        EscalationQueryService escalationQueryService =
                new EscalationQueryService(new EscalationInMemoryRepository(timezone));
        if (resetRepository) {
            ticketRepository = new TicketInMemoryRepository(escalationQueryService, timezone);
        }
        ticketProcessingService = new TicketProcessingService(
                ticketRepository,
                slackService,
                escalationQueryService,
                slackTicketsProps,
                new SlackChannelRegistry(slackTicketsProps),
                assignmentProps,
                publisher,
                Optional.empty(),
                rbacService,
                supportTeamProps);
    }

    private void assertStalenessTargetIsUser(String expectedUserId) {
        verify(slackService).warnStaleness(any(), stalenessTagTargetCaptor.capture());
        StalenessTagTarget target = stalenessTagTargetCaptor.getValue();
        assertInstanceOf(StalenessTagTarget.User.class, target);
        assertEquals(expectedUserId, ((StalenessTagTarget.User) target).userId());
    }

    private void assertStalenessTargetIsSquad() {
        verify(slackService).warnStaleness(any(), stalenessTagTargetCaptor.capture());
        StalenessTagTarget target = stalenessTagTargetCaptor.getValue();
        assertInstanceOf(StalenessTagTarget.Squad.class, target);
        assertEquals(SUPPORT_GROUP_ID, ((StalenessTagTarget.Squad) target).groupId());
    }

    @Test
    public void shouldUseLastThreadMessageTimeAsClosedAtViaCloseForPrResolution() {
        // given — ticket exists with a known last-message time
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        Instant lastMessageTime = Instant.parse("2024-01-15T10:00:00Z");
        ticketRepository.touchTicketById(ticketId, lastMessageTime);

        // when
        ticketProcessingService.closeForPrResolution(ticketId, ImmutableList.of("pr-review"), "low");

        // then — the closed status log entry uses the last thread message time
        Ticket closed = ticketRepository.findTicketById(ticketId);
        assertNotNull(closed);
        Instant closedAt = closed.statusLog().stream()
                .filter(l -> l.status() == TicketStatus.closed)
                .findFirst()
                .map(Ticket.StatusLog::date)
                .orElseThrow(() -> new AssertionError("No closed status log entry found"));
        assertEquals(lastMessageTime, closedAt, "Closed-at must equal the last thread message time");
    }

    @Test
    public void shouldUseLastThreadMessageTimeAsClosedAtViaSubmit() {
        // given — ticket exists with a known last-message time
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());

        Instant lastMessageTime = Instant.parse("2024-03-20T14:30:00Z");
        ticketRepository.touchTicketById(ticketId, lastMessageTime);

        TicketSubmission closeSubmission = TicketSubmission.builder()
                .ticketId(ticketId)
                .status(TicketStatus.closed)
                .authorsTeam(new TicketTeam.KnownTeam("platform"))
                .tags(ImmutableList.of("answered"))
                .impact("low")
                .confirmed(true)
                .build();

        // when
        ticketProcessingService.submit(closeSubmission);

        // then — the closed status log entry uses the last thread message time
        Ticket closed = ticketRepository.findTicketById(ticketId);
        assertNotNull(closed);
        Instant closedAt = closed.statusLog().stream()
                .filter(l -> l.status() == TicketStatus.closed)
                .findFirst()
                .map(Ticket.StatusLog::date)
                .orElseThrow(() -> new AssertionError("No closed status log entry found"));
        assertEquals(lastMessageTime, closedAt, "Closed-at must equal the last thread message time");
    }

    @Test
    public void shouldTouchTicketOnBotThreadReply() {
        // given — ticket exists
        Ticket ticket = createTrackedTicket();
        TicketId ticketId = requireNonNull(ticket.id());
        Instant initialLastInteracted = ticket.lastInteractedAt();

        // when — a bot message is posted as a thread reply (same shape as a normal message)
        MessageRef replyRef = new MessageRef(MessageTs.of("bot-reply-ts"), MESSAGE_TS, CHANNEL_ID);
        ticketProcessingService.handleMessagePosted(new MessagePosted("bot reply", "B_BOT_ID", replyRef));

        // then — lastInteractedAt is updated beyond the initial value
        Ticket updated = ticketRepository.findTicketById(ticketId);
        assertNotNull(updated);
        assertTrue(
                !updated.lastInteractedAt().isBefore(initialLastInteracted),
                "lastInteractedAt should be updated after bot thread reply");
    }

    private TicketProcessingService serviceWithPrDetection() {
        return new TicketProcessingService(
                ticketRepository,
                slackService,
                new EscalationQueryService(new EscalationInMemoryRepository(ZoneId.of("UTC"))),
                slackTicketsProps,
                new SlackChannelRegistry(slackTicketsProps),
                assignmentProps,
                publisher,
                Optional.of(prDetectionService),
                rbacService,
                supportTeamProps);
    }

    private Ticket createTrackedTicket() {
        MessageRef queryRef = new MessageRef(MESSAGE_TS, null, CHANNEL_ID);
        MessageRef formRef = new MessageRef(MessageTs.of("form-ts"), MESSAGE_TS, CHANNEL_ID);
        when(slackService.postTicketForm(any(), any())).thenReturn(formRef);
        ticketProcessingService.handleReactionAdded(
                new ReactionAdded(slackTicketsProps.expectedInitialReaction(), USER_ID, queryRef));
        return requireNonNull(ticketRepository.findTicketByQuery(queryRef));
    }
}
