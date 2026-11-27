package com.coreeng.supportbot.ticket.slack;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
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

    @Test
    void addReaction_addsTheReactionToTheMessage() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", false), "C123");

        service.addReaction(ref, "pr-merged");

        verify(slackClient)
                .addReaction(argThat(req -> "pr-merged".equals(req.getName())
                        && "C123".equals(req.getChannel())
                        && "1754593000".equals(req.getTimestamp())));
    }

    @Test
    void addReaction_toleratesAlreadyReacted() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", false), "C123");
        doThrow(slackError("already_reacted")).when(slackClient).addReaction(any());

        assertThatCode(() -> service.addReaction(ref, "pr-merged")).doesNotThrowAnyException();
    }

    @Test
    void addReaction_propagatesOtherSlackErrors() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", false), "C123");
        doThrow(slackError("invalid_name")).when(slackClient).addReaction(any());

        assertThatThrownBy(() -> service.addReaction(ref, "pr-merged")).isInstanceOf(SlackException.class);
    }

    @Test
    void removeReaction_removesTheReactionFromTheMessage() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", false), "C123");

        service.removeReaction(ref, "pr");

        verify(slackClient)
                .removeReaction(argThat(req -> "pr".equals(req.getName())
                        && "C123".equals(req.getChannel())
                        && "1754593000".equals(req.getTimestamp())));
    }

    @Test
    void removeReaction_toleratesNoReaction() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", false), "C123");
        doThrow(slackError("no_reaction")).when(slackClient).removeReaction(any());

        assertThatCode(() -> service.removeReaction(ref, "pr")).doesNotThrowAnyException();
    }

    @Test
    void removeReaction_propagatesOtherSlackErrors() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", false), "C123");
        doThrow(slackError("ratelimited")).when(slackClient).removeReaction(any());

        assertThatThrownBy(() -> service.removeReaction(ref, "pr")).isInstanceOf(SlackException.class);
    }

    @Test
    void reactions_skipMockedMessages() {
        MessageRef ref = new MessageRef(new MessageTs("1754593000", true), "C123");

        service.addReaction(ref, "pr-merged");
        service.removeReaction(ref, "pr");

        verifyNoInteractions(slackClient);
    }

    private static SlackException slackError(String code) {
        SlackException e = mock(SlackException.class);
        when(e.getError()).thenReturn(code);
        return e;
    }
}
