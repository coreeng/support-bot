package com.coreeng.supportbot.ticket.slack;

import static org.mockito.Mockito.verifyNoInteractions;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.TicketCreatedMessageMapper;
import com.coreeng.supportbot.ticket.TicketId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketSlackServiceImplTest {

    @Mock
    private SlackClient slackClient;

    @Mock
    private SlackTicketsProps slackTicketsProps;

    @Mock
    private TicketCreatedMessageMapper createdMessageMapper;

    @Mock
    private RatingRequestMessageMapper ratingReqMessageMapper;

    private TicketSlackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketSlackServiceImpl(
                slackClient, slackTicketsProps, createdMessageMapper, ratingReqMessageMapper);
    }

    @Test
    void postRatingRequest_skipsWhenUserIsSlackbot() {
        MessageRef queryRef = new MessageRef(new MessageTs("1754593000", false), "C123");
        TicketId ticketId = new TicketId(42L);
        String userId = SlackId.SLACKBOT.id();

        service.postRatingRequest(queryRef, ticketId, userId);

        verifyNoInteractions(slackClient, ratingReqMessageMapper);
    }
}
