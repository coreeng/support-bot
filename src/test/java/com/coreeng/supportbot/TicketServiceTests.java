package com.coreeng.supportbot;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.TicketProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.*;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.coreeng.supportbot.config.UtilsConfig.dateFormatter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TicketServiceTests {
    private static final MessageTs messageTs = MessageTs.of("some-message-ts");
    private static final String userId = "some-user-id";

    private TicketService ticketService;
    private TicketRepository ticketRepository;
    @Mock
    private SlackClient slackClient;
    private SlackTicketsProps slackTicketsProps;
    private TicketProps ticketProps;

    @Captor
    private ArgumentCaptor<SlackPostMessageRequest> postMessageCaptor;

    @BeforeEach
    public void setUp() {
        ticketRepository = new TicketInMemoryRepository();
        slackTicketsProps = new SlackTicketsProps(
            "some-channel-id",
            "eyes",
            "ticket"
        );
        ticketProps = new TicketProps(List.of(), List.of());
        ticketService = new TicketService(
            ticketRepository,
            slackClient,
            slackTicketsProps,
            ticketProps,
            dateFormatter
        );
    }

    @Test
    public void shouldCreateQueryOnMessage() {
        // when
        ticketService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            new MessageRef(
                messageTs,
                null,
                slackTicketsProps.channelId()
            )
        ));

        // then
        assertTrue(ticketRepository.queryExists(messageTs), "Query is created");
    }

    @Test
    public void shouldIgnoreMessageToDifferentChannel() {
        // when
        ticketService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            new MessageRef(
                messageTs,
                null,
                "some-random-channnel"
            )
        ));

        // then
        assertFalse(ticketRepository.queryExists(messageTs), "Event is ignored");
    }

    @Test
    public void shouldIgnoreMessageInThreads() {
        // when
        ticketService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            new MessageRef(
                messageTs,
                MessageTs.of("thread-ts"),
                slackTicketsProps.channelId()
            )
        ));

        // then
        assertFalse(ticketRepository.queryExists(messageTs), "Event is ignored");
    }

    @Test
    public void shouldCreateTicketOnEyes() {
        // given
        String postedMessageTs = "posted-message-ts";

        ChatPostMessageResponse postMessageResp = new ChatPostMessageResponse();
        postMessageResp.setOk(true);
        postMessageResp.setTs(postedMessageTs);
        when(slackClient.postMessage(postMessageCaptor.capture())).thenReturn(postMessageResp);

        // when
        ticketService.handleReactionAdded(new ReactionAdded(
            slackTicketsProps.expectedInitialReaction(),
            userId,
            new MessageRef(
                messageTs,
                null,
                slackTicketsProps.channelId()
            )
        ));

        // then
        assertTrue(ticketRepository.queryExists(messageTs), "Query is created");

        Ticket ticket = ticketRepository.findTicketByQuery(messageTs);
        assertNotNull(ticket, "Ticket is created");
        assertNotNull(ticket.id());
        assertEquals(TicketStatus.unresolved, ticket.status());
        assertEquals(messageTs, ticket.queryTs());

        verify(slackClient, description("Reaction is added on the query")).addReaction(ReactionsAddRequest.builder()
            .name(slackTicketsProps.responseInitialReaction())
            .channel(slackTicketsProps.channelId())
            .timestamp(messageTs.ts())
            .build());

        verify(slackClient, description("Ticket form is posted")).postMessage(postMessageCaptor.capture());
    }
}
