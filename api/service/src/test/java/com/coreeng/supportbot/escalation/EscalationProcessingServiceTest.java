package com.coreeng.supportbot.escalation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.SlackEscalationProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EscalationProcessingServiceTest {
  private EscalationProcessingService processingService;

  @Mock private EscalationRepository escalationRepository;
  @Mock private SlackEscalationProps slackEscalationProps;
  @Mock private EscalationCreatedMessageMapper escalationMapper;
  @Mock private SlackClient slackClient;
  @Mock private EscalationTeamsRegistry escalationTeamsRegistry;
  @Mock private SlackTicketsProps slackTicketsProps;

  @BeforeEach
  public void setup() {
    processingService =
        new EscalationProcessingService(
            escalationRepository,
            slackEscalationProps,
                escalationMapper,
            slackClient,
            escalationTeamsRegistry,
            slackTicketsProps);
  }

  @Test
  public void escalationShouldBeReturnedWithNoIdGivenDatabaseDidNotInsert() {
    // given
    CreateEscalationRequest escalationRequest =
        CreateEscalationRequest.builder()
            .ticket(Ticket.builder().id(new TicketId(1)).build())
            .team("some-team")
            .tags(ImmutableList.of("tag-1", "tag-2"))
            .build();

    when(escalationRepository.createIfNotExists(any(Escalation.class)))
        .thenReturn(Escalation.builder().id(null).build());

    // when
    Escalation escalation = processingService.createEscalation(escalationRequest);

    // then
    assertThat(escalation).isNotNull();
    assertThat(escalation.id()).isNull();
  }

  @Test
  public void shouldReturnExpectedEscalationWhenDatabaseInsertHappens() {
    // given
    CreateEscalationRequest escalationRequest =
        CreateEscalationRequest.builder()
            .ticket(Ticket.builder().id(new TicketId(1)).build())
            .team("some-team")
            .tags(ImmutableList.of("tag-1", "tag-2"))
            .build();
    Escalation expectedEscalation =
        Escalation.builder()
            .id(new EscalationId(1))
            .ticketId(new TicketId(1))
            .status(EscalationStatus.opened)
            .team("some-team")
            .tags(ImmutableList.of("tag-1", "tag-2"))
            .build();
    ChatPostMessageResponse chatPostMessageResponse = new ChatPostMessageResponse();
    chatPostMessageResponse.setTs("ts");
    when(escalationRepository.createIfNotExists(any(Escalation.class)))
        .thenReturn(Escalation.builder().id(new EscalationId(1)).build());
    when(slackClient.postMessage(any())).thenReturn(chatPostMessageResponse);
    when(escalationRepository.update(any(Escalation.class))).thenReturn(expectedEscalation);
    when(escalationTeamsRegistry.findEscalationTeamByName(any())).thenReturn(new EscalationTeam("some-team","someTeam","id"));

    // when
    Escalation escalation = processingService.createEscalation(escalationRequest);

    // then
    assertThat(escalation).isNotNull();
    assertThat(escalation.id()).isEqualTo(expectedEscalation.id());
    assertThat(escalation.status()).isEqualTo(expectedEscalation.status());
    assertThat(escalation.team()).isEqualTo(expectedEscalation.team());
    assertThat(escalation.tags()).isEqualTo(expectedEscalation.tags());
  }
}
