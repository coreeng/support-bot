package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import com.coreeng.supportbot.teamgroups.TeamGroup;
import com.coreeng.supportbot.teamgroups.TeamGroupService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The model name stamped on a snapshot comes from the model itself when it says who it is. */
class LlmSummaryServiceTest {

    private static final String CONFIGURED_MODEL = "gemini-2.5-flash";

    @Test
    void modelNameFallsBackToTheConfiguredNameWhenTheModelDoesNotSelfDescribe() {
        // Vertex's model does not override defaultRequestParameters(), so the configured id is the
        // only name available — and it is the right one for that provider.
        ChatModel silent = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return reply("ignored");
            }
        };

        assertThat(new LlmSummaryService(silent, props(), teamGroups()).modelName())
                .isEqualTo(CONFIGURED_MODEL);
    }

    @Test
    void modelNamePrefersWhatTheModelReportsAboutItself() {
        ChatModel selfDescribing = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return reply("ignored");
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return ChatRequestParameters.builder()
                        .modelName("something-else")
                        .build();
            }
        };

        assertThat(new LlmSummaryService(selfDescribing, props(), teamGroups()).modelName())
                .isEqualTo("something-else");
    }

    @Test
    void modelNameIgnoresABlankSelfDescription() {
        ChatModel blank = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return reply("ignored");
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return ChatRequestParameters.builder().modelName(" ").build();
            }
        };

        assertThat(new LlmSummaryService(blank, props(), teamGroups()).modelName())
                .isEqualTo(CONFIGURED_MODEL);
    }

    @Test
    void aTeamGroupsReportSaysWhichTeamsItIsLimitedTo() {
        RecordingModel model = new RecordingModel();
        TeamGroup group = new TeamGroup("area-a", "Area A", ImmutableSortedSet.of("team-b", "team-a"));

        new LlmSummaryService(model, props(), teamGroups())
                .generate("Summarise.", new SummaryScope(WINDOW, group), breakdowns(), ImmutableList.of());

        assertThat(model.lastPrompt)
                .contains("Window: 2026-03-10 to 2026-03-23 (inclusive)\n"
                        + "Scope: Domain \"Area A\", which covers only tickets raised by these tenant teams:"
                        + " team-a, team-b. Every count and reason below is limited to these teams.\n"
                        + "Tickets raised in the window: 3\n");
    }

    @Test
    void theWholeWindowsReportHasNoScopeLine() {
        RecordingModel model = new RecordingModel();

        new LlmSummaryService(model, props(), teamGroups())
                .generate("Summarise.", SummaryScope.all(WINDOW), breakdowns(), ImmutableList.of());

        assertThat(model.lastPrompt)
                .contains("Window: 2026-03-10 to 2026-03-23 (inclusive)\nTickets raised in the window: 3\n")
                .doesNotContain("Scope:");
    }

    private static final SummaryWindow WINDOW = new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));

    /** Remembers the last prompt it was sent. */
    private static final class RecordingModel implements ChatModel {
        String lastPrompt = "";

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastPrompt = ((UserMessage) chatRequest.messages().getLast()).singleText();
            return reply("prose");
        }
    }

    private static SummaryBreakdowns breakdowns() {
        return new SummaryBreakdowns(
                WINDOW,
                3,
                2,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(new SummaryCount("team-a", 3)),
                ImmutableList.of());
    }

    private static TeamGroupService teamGroups() {
        TeamGroupService teamGroups = mock(TeamGroupService.class);
        lenient().when(teamGroups.displayName()).thenReturn("Domain");
        return teamGroups;
    }

    private static ChatResponse reply(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private static LlmProps props() {
        return new LlmProps(
                LlmProvider.VERTEX,
                CONFIGURED_MODEL,
                Duration.ofMillis(1),
                new LlmProps.Vertex("test-project", "europe-west2"),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Stub(false));
    }
}
