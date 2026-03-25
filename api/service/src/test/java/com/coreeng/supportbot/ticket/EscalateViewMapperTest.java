package com.coreeng.supportbot.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.escalation.EscalationValidator;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EscalateViewMapperTest {

    // Use a real JsonMapper so we can deserialize the private EscalateViewMapper.Input record.
    private final JsonMapper jsonMapper = new JsonMapper();

    @Mock
    private EscalationValidator escalationValidator;

    @Mock
    private EscalationTeamsRegistry escalationTeamsRegistry;

    private EscalateViewMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EscalateViewMapper(jsonMapper, escalationValidator, escalationTeamsRegistry);
    }

    @Test
    void extractSubmittedValues_noTagsSelected_returnsEmptyTagsList() {
        ViewState.Value tagsValue = mock(ViewState.Value.class);
        when(tagsValue.getSelectedOptions()).thenReturn(null);

        ViewState.SelectedOption selectedTeam = mock(ViewState.SelectedOption.class);
        when(selectedTeam.getValue()).thenReturn("platform-team");
        ViewState.Value teamValue = mock(ViewState.Value.class);
        when(teamValue.getSelectedOption()).thenReturn(selectedTeam);

        View view = buildView(
                "{\"ticketId\":42}",
                Map.of(
                        "escalation-tags", tagsValue,
                        "escalation-team", teamValue));

        EscalateRequest result = mapper.extractSubmittedValues(view);

        assertThat(result.tags()).isEmpty();
        assertThat(result.ticketId()).isEqualTo(new TicketId(42L));
    }

    @Test
    void extractSubmittedValues_withTags_returnsTagCodes() {
        ViewState.SelectedOption selectedTag = mock(ViewState.SelectedOption.class);
        when(selectedTag.getValue()).thenReturn("networking");
        ViewState.Value tagsValue = mock(ViewState.Value.class);
        when(tagsValue.getSelectedOptions()).thenReturn(ImmutableList.of(selectedTag));

        ViewState.SelectedOption selectedTeam = mock(ViewState.SelectedOption.class);
        when(selectedTeam.getValue()).thenReturn("platform-team");
        ViewState.Value teamValue = mock(ViewState.Value.class);
        when(teamValue.getSelectedOption()).thenReturn(selectedTeam);

        View view = buildView(
                "{\"ticketId\":42}",
                Map.of(
                        "escalation-tags", tagsValue,
                        "escalation-team", teamValue));

        EscalateRequest result = mapper.extractSubmittedValues(view);

        assertThat(result.tags()).containsExactly("networking");
    }

    private View buildView(String privateMetadata, Map<String, ViewState.Value> actionValues) {
        View view = mock(View.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(view.getPrivateMetadata()).thenReturn(privateMetadata);
        // Slack wraps each action-ID value inside a block-ID map
        Map<String, Map<String, ViewState.Value>> blockValues = actionValues.entrySet().stream()
                .collect(Collectors.toMap(e -> "block-" + e.getKey(), e -> Map.of(e.getKey(), e.getValue())));
        when(view.getState().getValues()).thenReturn(blockValues);
        return view;
    }
}
