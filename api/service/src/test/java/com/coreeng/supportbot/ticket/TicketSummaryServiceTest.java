package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamMemberFetcher;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import com.slack.api.model.block.ContextBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.view.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSummaryServiceTest {

    @Mock
    private TicketRepository repository;
    @Mock
    private SlackClient slackClient;

    @Mock
    private EscalationQueryService escalationQueryService;

    @Mock
    private TagsRegistry tagsRegistry;

    @Mock
    private ImpactsRegistry impactsRegistry;

    @Mock
    private EscalationTeamsRegistry escalationTeamsRegistry;

    @Mock
    private SupportTeamService supportTeamService;

    private TicketSummaryService service;
    private TicketSummaryViewMapper summaryViewMapper;

    private TicketId ticketId;
    private String channelId;
    private MessageTs queryTs;
    private Ticket ticket;
    private Message slackMessage;

    @BeforeEach
    void setUp() {
        ticketId = new TicketId(123L);
        channelId = "C123456";
        queryTs = MessageTs.of("1234567890.123456");

        ticket = Ticket.builder()
            .id(ticketId)
            .channelId(channelId)
            .queryTs(queryTs)
            .status(TicketStatus.opened)
            .team(TicketTeam.fromCode("core-team"))
            .tags(ImmutableList.of("bug", "urgent"))
            .impact("production-blocking")
            .assignedTo(null)
            .build();

        slackMessage = new Message();
        slackMessage.setTs(queryTs.ts());
        slackMessage.setUser("U123456");
        slackMessage.setText("Test query message");
        // Create a simple section block for testing
        SectionBlock sectionBlock = new SectionBlock();
        slackMessage.setBlocks(ImmutableList.of(sectionBlock));

        JsonMapper jsonMapper = new JsonMapper();
        summaryViewMapper = new TicketSummaryViewMapper(jsonMapper);

        TicketAssignmentProps defaultAssignmentProps = new TicketAssignmentProps(
            false,
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            defaultAssignmentProps
        );

        lenient().when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        lenient().when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());
    }

    @Test
    void shouldReturnSummaryViewWhenAssignmentDisabled() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        when(repository.findTicketById(ticketId)).thenReturn(ticket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(
            new Tag("Bug", "bug"),
            new Tag("Urgent", "urgent")
        ));
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of(
            new TicketImpact("Production Blocking", "production-blocking")
        ));

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentAssignee()).isNull();
        assertThat(result.availableAssignees()).isEmpty();
        verify(supportTeamService, never()).members();
    }

    @Test
    void shouldReturnSummaryViewWithAssigneeFieldsWhenEnabled() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            true, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        Ticket assignedTicket = ticket.toBuilder()
            .assignedTo(SlackId.user("U789012"))
            .build();

        ImmutableList<TeamMemberFetcher.TeamMember> members = ImmutableList.of(
            new TeamMemberFetcher.TeamMember("alice@example.com", SlackId.user("U789012")),
            new TeamMemberFetcher.TeamMember("bob@example.com", SlackId.user("U789013"))
        );

        when(repository.findTicketById(ticketId)).thenReturn(assignedTicket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());
        when(supportTeamService.members()).thenReturn(members);

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentAssignee()).isEqualTo("U789012");
        assertThat(result.availableAssignees()).hasSize(2);
        assertThat(result.availableAssignees().get(0).userId()).isEqualTo("U789012");
        assertThat(result.availableAssignees().get(0).displayName()).isEqualTo("alice@example.com");
        assertThat(result.availableAssignees().get(1).userId()).isEqualTo("U789013");
        assertThat(result.availableAssignees().get(1).displayName()).isEqualTo("bob@example.com");
        verify(supportTeamService).members();
    }

    @Test
    void shouldIncludeEscalationsInSummary() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        Escalation escalation1 = Escalation.builder()
            .id(new EscalationId(1L))
            .ticketId(ticketId)
            .channelId(channelId)
            .threadTs(MessageTs.of("1111111111.111111"))
            .team("platform-team")
            .openedAt(Instant.parse("2024-01-01T10:00:00Z"))
            .build();

        Escalation escalation2 = Escalation.builder()
            .id(new EscalationId(2L))
            .ticketId(ticketId)
            .channelId(channelId)
            .threadTs(MessageTs.of("2222222222.222222"))
            .team("security-team")
            .openedAt(Instant.parse("2024-01-01T11:00:00Z"))
            .build();

        when(repository.findTicketById(ticketId)).thenReturn(ticket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class)))
            .thenReturn("https://slack.com/query-permalink")
            .thenReturn("https://slack.com/escalation1-permalink")
            .thenReturn("https://slack.com/escalation2-permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of(escalation2, escalation1)); // reversed order
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());
        when(escalationTeamsRegistry.findEscalationTeamByCode("platform-team"))
            .thenReturn(new EscalationTeam("Platform Team", "platform-team", "platform-support"));
        when(escalationTeamsRegistry.findEscalationTeamByCode("security-team"))
            .thenReturn(new EscalationTeam("Security Team", "security-team", "security-group"));

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result.escalations()).hasSize(2);
        // Should be sorted by openedAt
        assertThat(result.escalations().get(0).teamSlackGroupId()).isEqualTo("platform-support");
        assertThat(result.escalations().get(1).teamSlackGroupId()).isEqualTo("security-group");
    }

    @Test
    void shouldIncludeQuerySummaryInView() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        when(repository.findTicketById(ticketId)).thenReturn(ticket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result.query()).isNotNull();
        assertThat(result.query().messageTs()).isEqualTo(queryTs);
        assertThat(result.query().senderId()).isEqualTo(SlackId.user("U123456"));
        assertThat(result.query().permalink()).isEqualTo("https://slack.com/permalink");
        assertThat(result.query().blocks()).hasSize(1);
    }

    @Test
    void shouldHandleBotMessage() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        Message botMessage = new Message();
        botMessage.setTs(queryTs.ts());
        botMessage.setUser(null);
        botMessage.setBotId("B123456");
        botMessage.setBlocks(ImmutableList.of());

        when(repository.findTicketById(ticketId)).thenReturn(ticket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(botMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result.query().senderId()).isEqualTo(SlackId.bot("B123456"));
    }

    @Test
    void shouldThrowExceptionWhenTicketNotFound() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        when(repository.findTicketById(ticketId)).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> service.summaryView(ticketId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ticket not found");
    }

    @Test
    void shouldFilterSelectedTagsCorrectly() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        when(repository.findTicketById(ticketId)).thenReturn(ticket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(
            new Tag("Bug", "bug"),
            new Tag("Urgent", "urgent"),
            new Tag("Feature", "feature")
        ));
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result.tags()).hasSize(3);
        assertThat(result.currentTags()).hasSize(2);
        assertThat(result.currentTags().stream().map(Tag::code))
            .containsExactlyInAnyOrder("bug", "urgent");
    }

    @Test
    void shouldFilterSelectedImpactCorrectly() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        when(repository.findTicketById(ticketId)).thenReturn(ticket);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of(
            new TicketImpact("Production Blocking", "production-blocking"),
            new TicketImpact("Minor Issue", "minor-issue")
        ));

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result.impacts()).hasSize(2);
        assertThat(result.currentImpact()).isNotNull();
        assertThat(result.currentImpact().code()).isEqualTo("production-blocking");
    }

    @Test
    void shouldHandleNullImpact() {
        // given
        TicketAssignmentProps assignmentProps = new TicketAssignmentProps(
            false, 
            new TicketAssignmentProps.Encryption(false, null)
        );
        service = new TicketSummaryService(
            repository,
            slackClient,
            escalationQueryService,
            tagsRegistry,
            impactsRegistry,
            escalationTeamsRegistry,
            supportTeamService,
            assignmentProps
        );

        Ticket ticketWithoutImpact = ticket.toBuilder()
            .impact(null)
            .build();

        when(repository.findTicketById(ticketId)).thenReturn(ticketWithoutImpact);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(slackMessage);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.com/permalink");
        when(escalationQueryService.listByTicketId(ticketId)).thenReturn(ImmutableList.of());
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of());
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of(
            new TicketImpact("Production Blocking", "production-blocking")
        ));

        // when
        TicketSummaryView result = service.summaryView(ticketId);

        // then
        assertThat(result.currentImpact()).isNull();
    }

    private Ticket sampleTicket() {
        return Ticket.builder()
            .id(new TicketId(1))
            .channelId("C123")
            .queryTs(MessageTs.of("123.456"))
            .createdMessageTs(MessageTs.of("123.456"))
            .status(TicketStatus.opened)
            .lastInteractedAt(Instant.EPOCH)
            .build();
    }

    @Test
    void summaryView_handlesTombstoneMessage() {
        Ticket ticket = sampleTicket();
        when(repository.findTicketById(ticket.id())).thenReturn(ticket);
        when(escalationQueryService.listByTicketId(ticket.id())).thenReturn(ImmutableList.of());

        Message tombstone = new Message();
        tombstone.setSubtype("tombstone");
        tombstone.setText("This message was deleted");

        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(tombstone);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class))).thenReturn("https://slack.test/permalink");

        TicketSummaryView summary = service.summaryView(ticket.id());
        TicketSummaryView.QuerySummaryView query = summary.query();

        assertEquals(ticket.queryTs(), query.messageTs());
        assertNull(query.senderId());
        assertEquals("https://slack.test/permalink", query.permalink());
        ImmutableList<LayoutBlock> expectedBlocks = ImmutableList.of(
            section(s -> s.text(markdownText(t ->
                t.text("This message was deleted"))))
        );
        assertEquals(expectedBlocks, query.blocks());
    }

    @Test
    void summaryView_handlesGetMessageByTsFailure_andPermalinkSuccessAndFailure() {
        Ticket ticket = sampleTicket();
        when(repository.findTicketById(ticket.id())).thenReturn(ticket);
        when(escalationQueryService.listByTicketId(ticket.id())).thenReturn(ImmutableList.of());

        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class)))
            .thenThrow(new SlackException(new RuntimeException("boom")));
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class)))
            .thenReturn("https://slack.test/permalink")
            .thenThrow(new SlackException(new RuntimeException("permalink boom")));

        TicketSummaryView first = service.summaryView(ticket.id());
        TicketSummaryView.QuerySummaryView firstQuery = first.query();
        assertEquals(ticket.queryTs(), firstQuery.messageTs());
        assertNull(firstQuery.senderId());
        assertEquals("https://slack.test/permalink", firstQuery.permalink());
        ImmutableList<LayoutBlock> expectedFirstBlocks = ImmutableList.of(
            section(s -> s.text(markdownText(t -> t.text("Couldn't fetch the message"))))
        );
        assertEquals(expectedFirstBlocks, firstQuery.blocks());

        View firstView = view(v -> summaryViewMapper.render(first, v).type("modal"));
        ContextBlock firstContext = (ContextBlock) firstView.getBlocks().stream()
            .filter(b -> b instanceof ContextBlock)
            .findFirst()
            .orElseThrow();
        MarkdownTextObject firstContextText = (MarkdownTextObject) firstContext.getElements().get(0);
        assertTrue(firstContextText.getText().contains("Unknown author"));
        assertTrue(firstContextText.getText().contains("View Message"));

        TicketSummaryView second = service.summaryView(ticket.id());
        TicketSummaryView.QuerySummaryView secondQuery = second.query();
        assertNull(secondQuery.permalink());

        View secondView = view(v -> summaryViewMapper.render(second, v).type("modal"));
        ContextBlock secondContext = (ContextBlock) secondView.getBlocks().stream()
            .filter(b -> b instanceof ContextBlock)
            .findFirst()
            .orElseThrow();
        MarkdownTextObject secondContextText = (MarkdownTextObject) secondContext.getElements().getFirst();
        assertTrue(secondContextText.getText().contains("Unknown author"));
        assertFalse(secondContextText.getText().contains("View Message"));
    }

    @Test
    void summaryView_handlesPermalinkFailureWhenMessageFetchSucceeds() {
        Ticket ticket = sampleTicket();
        when(repository.findTicketById(ticket.id())).thenReturn(ticket);
        when(escalationQueryService.listByTicketId(ticket.id())).thenReturn(ImmutableList.of());

        Message message = new Message();
        message.setUser("U123");
        message.setText("Hello");
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(message);
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class)))
            .thenThrow(new SlackException(new RuntimeException("permalink boom")));

        TicketSummaryView summary = service.summaryView(ticket.id());
        TicketSummaryView.QuerySummaryView query = summary.query();

        assertEquals(ticket.queryTs(), query.messageTs());
        ImmutableList<LayoutBlock> expectedBlocks = ImmutableList.of(
            section(s -> s.text(markdownText(t -> t.text("Hello"))))
        );
        assertEquals(expectedBlocks, query.blocks());
        assertNull(query.permalink());

        View view = view(v -> summaryViewMapper.render(summary, v).type("modal"));
        ContextBlock context = (ContextBlock) view.getBlocks().stream()
            .filter(b -> b instanceof ContextBlock)
            .findFirst()
            .orElseThrow();
        MarkdownTextObject contextText = (MarkdownTextObject) context.getElements().get(0);
        assertTrue(contextText.getText().contains("<@U123>"));
        assertFalse(contextText.getText().contains("View Message"));

    }
}

