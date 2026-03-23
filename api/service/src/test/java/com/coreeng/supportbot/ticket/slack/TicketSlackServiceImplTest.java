package com.coreeng.supportbot.ticket.slack;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.StalenessTagTarget;
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
    void warnStaleness_postsMessageWithUserMention() {
        MessageRef queryRef = new MessageRef(new MessageTs("1754593000", false), "C123");
        StalenessTagTarget target = new StalenessTagTarget.User("U_ENGINEER");

        service.warnStaleness(queryRef, target);

        verify(slackClient)
                .postMessage(argThat(
                        req -> req.message().getText().contains("<@U_ENGINEER>") && "C123".equals(req.channel())));
    }

    @Test
    void warnStaleness_postsMessageWithSquadMention() {
        MessageRef queryRef = new MessageRef(new MessageTs("1754593000", false), "C123");
        StalenessTagTarget target = new StalenessTagTarget.Squad("S08948NBMED");

        service.warnStaleness(queryRef, target);

        verify(slackClient)
                .postMessage(argThat(req ->
                        req.message().getText().contains("<!subteam^S08948NBMED>") && "C123".equals(req.channel())));
    }

    @Test
    void warnStaleness_skipsWhenMocked() {
        MessageRef queryRef = new MessageRef(new MessageTs("1754593000", true), "C123");
        StalenessTagTarget target = new StalenessTagTarget.User("U_ENGINEER");

        service.warnStaleness(queryRef, target);

        verifyNoInteractions(slackClient);
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
