package com.coreeng.supportbot.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import com.slack.api.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketTeamSuggestionsServiceTest {

    @Mock
    private SlackClient slackClient;

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private TicketRepository ticketRepository;

    private TicketTeamSuggestionsService service;

    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        service = new TicketTeamSuggestionsService(slackClient, platformTeamsService, ticketRepository);
        ticketId = new TicketId(123L);
    }

    @Test
    void getTeamSuggestionsForTicket_returnsGroupedTeams() {
        // given
        Ticket ticket = ticketWithQueryTs(ticketId);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);

        Message message = new Message();
        message.setUser("U456");
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(message);

        User user = new User();
        User.Profile profile = new User.Profile();
        profile.setEmail("user@example.com");
        user.setProfile(profile);
        when(slackClient.getUserById(SlackId.user("U456"))).thenReturn(user);

        when(platformTeamsService.listTeamsByUserEmail("user@example.com"))
                .thenReturn(ImmutableList.of(new PlatformTeam("AuthorTeam", Set.of(), Set.of())));
        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(
                        new PlatformTeam("AuthorTeam", Set.of(), Set.of()),
                        new PlatformTeam("OtherTeam", Set.of(), Set.of())));

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userTeams()).containsExactly("AuthorTeam");
        assertThat(result.get().otherTeams()).containsExactly("OtherTeam");
    }

    @Test
    void getTeamSuggestionsForTicket_ticketNotFound_returnsEmpty() {
        // given
        when(ticketRepository.findTicketById(ticketId)).thenReturn(null);

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getTeamSuggestionsForTicket_slackbotAuthor_returnsFallback() {
        // given
        Ticket ticket = ticketWithQueryTs(ticketId);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);

        Message message = new Message();
        message.setUser(SlackId.SLACKBOT.id());
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(message);

        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(
                        new PlatformTeam("TeamA", Set.of(), Set.of()), new PlatformTeam("TeamB", Set.of(), Set.of())));

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userTeams()).isEmpty();
        assertThat(result.get().otherTeams()).containsExactly("TeamA", "TeamB");
    }

    @Test
    void getTeamSuggestionsForTicket_slackError_returnsFallback() {
        // given
        Ticket ticket = ticketWithQueryTs(ticketId);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);

        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class)))
                .thenThrow(new SlackException(new RuntimeException("Slack API error")));

        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(new PlatformTeam("TeamA", Set.of(), Set.of())));

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userTeams()).isEmpty();
        assertThat(result.get().otherTeams()).containsExactly("TeamA");
    }

    @Test
    void getTeamSuggestionsForTicket_nullAuthor_returnsFallback() {
        // given
        Ticket ticket = ticketWithQueryTs(ticketId);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);

        Message message = new Message();
        // both user and botId are null by default
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(message);

        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(new PlatformTeam("TeamA", Set.of(), Set.of())));

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userTeams()).isEmpty();
        assertThat(result.get().otherTeams()).containsExactly("TeamA");
    }

    @Test
    void getTeamSuggestionsForTicket_nullMessage_returnsFallback() {
        // given
        Ticket ticket = ticketWithQueryTs(ticketId);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);

        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(null);

        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(new PlatformTeam("TeamA", Set.of(), Set.of())));

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userTeams()).isEmpty();
        assertThat(result.get().otherTeams()).containsExactly("TeamA");
    }

    @Test
    void getTeamSuggestionsForTicket_botAuthor_returnsSuggestions() {
        // given
        Ticket ticket = ticketWithQueryTs(ticketId);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);

        Message message = new Message();
        message.setUser(null);
        message.setBotId("B789");
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(message);

        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(
                        new PlatformTeam("TeamA", Set.of(), Set.of()), new PlatformTeam("TeamB", Set.of(), Set.of())));

        // when
        Optional<TicketTeamsSuggestion> result = service.getTeamSuggestionsForTicket(ticketId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userTeams()).isEmpty();
        assertThat(result.get().otherTeams()).containsExactly("TeamA", "TeamB");
    }

    private static Ticket ticketWithQueryTs(TicketId id) {
        return Ticket.builder()
                .id(id)
                .channelId("C123")
                .queryTs(MessageTs.of("111.222"))
                .status(TicketStatus.opened)
                .lastInteractedAt(Instant.now())
                .build();
    }
}
