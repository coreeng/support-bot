package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.TicketSummaryView;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.ticket.TicketTeamSuggestionsService;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.interactive_components.response.BlockSuggestionResponse;
import com.slack.api.app_backend.interactive_components.response.Option;
import com.slack.api.app_backend.interactive_components.response.OptionGroup;
import com.slack.api.bolt.context.builtin.BlockSuggestionContext;
import com.slack.api.bolt.request.builtin.BlockSuggestionRequest;
import com.slack.api.model.User;
import com.slack.api.model.view.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketTeamSuggestionHandlerTest {
    @Mock
    private SlackClient slackClient;
    @Mock
    private PlatformTeamsService platformTeamsService;

    private TicketTeamSuggestionHandler handler;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        handler = new TicketTeamSuggestionHandler(
            new TicketTeamSuggestionsService(slackClient, platformTeamsService),
            new TicketSummaryViewMapper(jsonMapper)
        );
    }

    @Test
    void noTeamsExist_returnsNotATenantOnly() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "user@example.com");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of());
        when(platformTeamsService.listTeamsByUserEmail("user@example.com")).thenReturn(ImmutableList.of());

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(1, groups.size());
        assertEquals("Suggested teams", groups.get(0).getLabel().getText());
        assertOptionsEqual(groups.get(0).getOptions(), ImmutableList.of("Not a Tenant"));
        verify(slackClient).getUserById(new SlackId.User("U123"));
    }

    @Test
    void onlyUserTeams_rendersSuggestedTeamsGroup() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "user@example.com");

        PlatformTeam t1 = team("Team A");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(t1));
        when(platformTeamsService.listTeamsByUserEmail("user@example.com")).thenReturn(ImmutableList.of(t1));

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(1, groups.size());
        assertEquals("Suggested teams", groups.getFirst().getLabel().getText());
        assertOptionsEqual(groups.getFirst().getOptions(), ImmutableList.of("Team A"));
    }

    @Test
    void onlyOtherTeams_rendersNotATenantAndOthersGroup() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "user@example.com");

        PlatformTeam t1 = team("Team A");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(t1));
        when(platformTeamsService.listTeamsByUserEmail("user@example.com")).thenReturn(ImmutableList.of());

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(2, groups.size());
        assertEquals("Suggested teams", groups.get(0).getLabel().getText());
        assertOptionsEqual(groups.get(0).getOptions(), ImmutableList.of("Not a Tenant"));
        assertEquals("Others", groups.get(1).getLabel().getText());
        assertOptionsEqual(groups.get(1).getOptions(), ImmutableList.of("Team A"));
    }

    @Test
    void bothGroups_rendersSuggestedFirstThenOthers() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "user@example.com");

        PlatformTeam t1 = team("Team A");
        PlatformTeam t2 = team("Team B");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(t1, t2));
        when(platformTeamsService.listTeamsByUserEmail("user@example.com")).thenReturn(ImmutableList.of(t1));

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(2, groups.size());
        assertEquals("Suggested teams", groups.get(0).getLabel().getText());
        assertOptionsEqual(groups.get(0).getOptions(), ImmutableList.of("Team A"));
        assertEquals("Others", groups.get(1).getLabel().getText());
        assertOptionsEqual(groups.get(1).getOptions(), ImmutableList.of("Team B"));
    }

    @Test
    void userHasNoTeams_suggestsNotATenant() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "unknown@example.com");

        PlatformTeam t1 = team("Team A");
        PlatformTeam t2 = team("Team B");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(t1, t2));
        when(platformTeamsService.listTeamsByUserEmail("unknown@example.com")).thenReturn(ImmutableList.of());

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(2, groups.size());
        assertEquals("Suggested teams", groups.get(0).getLabel().getText());
        assertOptionsEqual(groups.get(0).getOptions(), ImmutableList.of("Not a Tenant"));
        assertEquals("Others", groups.get(1).getLabel().getText());
        assertOptionsEqual(groups.get(1).getOptions(), ImmutableList.of("Team A", "Team B"));
    }

    @Test
    void filterValue_isAppliedToBothLists() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("be", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "user@example.com");

        PlatformTeam alpha = team("Alpha");
        PlatformTeam beta = team("Beta");
        PlatformTeam zeta = team("Zeta");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(alpha, beta, zeta));
        when(platformTeamsService.listTeamsByUserEmail("user@example.com")).thenReturn(ImmutableList.of(beta));

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        // Only Beta contains "be" (case-insensitive) → one group "Suggested teams" with Beta
        assertEquals(1, groups.size());
        assertEquals("Suggested teams", groups.get(0).getLabel().getText());
        assertOptionsEqual(groups.get(0).getOptions(), ImmutableList.of("Beta"));
    }

    @Test
    void optionsAreLimitedTo100_perGroup() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);
        mockSlackUserEmail("U123", "user@example.com");

        ImmutableList<String> userTeamNames = ImmutableList.copyOf(IntStream.range(0, 150).mapToObj(i -> "U" + i).toList());
        ImmutableList<String> otherTeamNames = ImmutableList.copyOf(IntStream.range(0, 150).mapToObj(i -> "O" + i).toList());

        ImmutableList<PlatformTeam> allTeams = ImmutableList.<PlatformTeam>builder()
            .addAll(userTeamNames.stream().map(this::team).toList())
            .addAll(otherTeamNames.stream().map(this::team).toList())
            .build();

        when(platformTeamsService.listTeams()).thenReturn(allTeams);
        when(platformTeamsService.listTeamsByUserEmail("user@example.com"))
            .thenReturn(userTeamNames.stream().map(this::team).collect(ImmutableList.toImmutableList()));

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(2, groups.size());

        // Suggested teams → first 100 U*
        List<Option> suggested = groups.get(0).getOptions();
        assertEquals(100, suggested.size());
        assertEquals("U0", suggested.getFirst().getText().getText());
        assertEquals("U99", suggested.getLast().getText().getText());

        // Others → first 100 O*
        List<Option> others = groups.get(1).getOptions();
        assertEquals(100, others.size());
        assertEquals("O0", others.getFirst().getText().getText());
        assertEquals("O99", others.getLast().getText().getText());
    }

    @Test
    void slackbotAuthor_usesFallbackSuggestions() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", SlackId.slackbot.id());
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);

        PlatformTeam t1 = team("Team A");
        PlatformTeam t2 = team("Team B");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(t1, t2));

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(1, groups.size());
        assertEquals("Others", groups.getFirst().getLabel().getText());
        assertOptionsEqual(groups.getFirst().getOptions(), ImmutableList.of("Team A", "Team B"));
        verifyNoInteractions(slackClient);
    }

    @Test
    void errorGettingTeamSuggestions_usesFallbackSuggestions() {
        // given
        BlockSuggestionRequest req = mockSuggestionRequest("", "U123");
        BlockSuggestionContext ctx = mock(BlockSuggestionContext.class);

        PlatformTeam t1 = team("Team A");
        PlatformTeam t2 = team("Team B");
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(t1, t2));
        when(slackClient.getUserById(new SlackId.User("U123")))
            .thenThrow(new RuntimeException("boom"));

        // when
        BlockSuggestionResponse resp = handler.apply(req, ctx);

        // then
        List<OptionGroup> groups = resp.getOptionGroups();
        assertEquals(1, groups.size());
        assertEquals("Others", groups.get(0).getLabel().getText());
        assertOptionsEqual(groups.get(0).getOptions(), ImmutableList.of("Team A", "Team B"));
    }

    private void mockSlackUserEmail(String userId, String email) {
        User.Profile profile = new User.Profile();
        profile.setEmail(email);
        User user = new User();
        user.setProfile(profile);
        when(slackClient.getUserById(new SlackId.User(userId))).thenReturn(user);
    }

    private BlockSuggestionRequest mockSuggestionRequest(String value, String metadataAuthorId) {
        BlockSuggestionRequest req = mock(BlockSuggestionRequest.class, RETURNS_DEEP_STUBS);
        when(req.getPayload().getValue()).thenReturn(value);
        String metaJson = jsonMapper.toJsonString(new TicketSummaryView.Metadata(42L, new SlackId.User(metadataAuthorId)));
        View view = View.builder()
            .privateMetadata(metaJson)
            .build();
        when(req.getPayload().getView()).thenReturn(view);
        return req;
    }

    private PlatformTeam team(String name) {
        return new PlatformTeam(name, Set.of(), Set.of());
    }

    private static void assertOptionsEqual(List<Option> options, List<String> expectedNames) {
        assertEquals(expectedNames.size(), options.size());
        for (int i = 0; i < expectedNames.size(); i++) {
            String name = expectedNames.get(i);
            assertEquals(name, options.get(i).getText().getText());
            assertEquals(name, options.get(i).getValue());
        }
    }
}
