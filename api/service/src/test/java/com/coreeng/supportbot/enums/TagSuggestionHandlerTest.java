package com.coreeng.supportbot.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.homepage.HomepageFilterMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.interactive_components.response.BlockSuggestionResponse;
import com.slack.api.app_backend.interactive_components.response.Option;
import com.slack.api.bolt.context.builtin.BlockSuggestionContext;
import com.slack.api.bolt.request.builtin.BlockSuggestionRequest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagSuggestionHandlerTest {

    @Mock
    private TagsRegistry tagsRegistry;

    private TagSuggestionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TagSuggestionHandler(tagsRegistry);
    }

    @Test
    void pattern_matchesAllThreeActionIds() {
        assertThat(handler.getPattern().matcher("homepage-filter-tags").matches())
                .isTrue();
        assertThat(handler.getPattern().matcher("escalation-tags").matches()).isTrue();
        assertThat(handler.getPattern().matcher("ticket-change-tags").matches()).isTrue();
        assertThat(handler.getPattern().matcher("something-else").matches()).isFalse();
    }

    @Test
    void emptyQuery_returnsAllActiveTags_forEscalationContext() {
        when(tagsRegistry.listAllTags())
                .thenReturn(ImmutableList.of(new Tag("Networking", "networking"), new Tag("Ingresses", "ingresses")));

        BlockSuggestionResponse response =
                handler.apply(request("escalation-tags", ""), mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).containsExactly("networking", "ingresses");
        assertThat(optionLabels(response)).containsExactly("Networking", "Ingresses");
    }

    @Test
    void emptyQuery_forHomepageFilter_prependsNoTagsOption() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Networking", "networking")));

        BlockSuggestionResponse response =
                handler.apply(request("homepage-filter-tags", ""), mock(BlockSuggestionContext.class));

        List<String> values = optionValues(response);
        assertThat(values.get(0)).isEqualTo(HomepageFilterMapper.NO_TAGS_VALUE);
        assertThat(optionLabels(response).get(0)).isEqualTo(HomepageFilterMapper.NO_TAGS_LABEL);
        assertThat(values).containsExactly(HomepageFilterMapper.NO_TAGS_VALUE, "networking");
    }

    @Test
    void noTagsOption_notIncluded_forEscalationContext() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Vault", "vault")));

        BlockSuggestionResponse response =
                handler.apply(request("escalation-tags", ""), mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).doesNotContain(HomepageFilterMapper.NO_TAGS_VALUE);
    }

    @Test
    void noTagsOption_notIncluded_forTicketChangeContext() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Vault", "vault")));

        BlockSuggestionResponse response =
                handler.apply(request("ticket-change-tags", ""), mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).doesNotContain(HomepageFilterMapper.NO_TAGS_VALUE);
    }

    @Test
    void query_filtersByLabelCaseInsensitive() {
        when(tagsRegistry.listAllTags())
                .thenReturn(ImmutableList.of(
                        new Tag("Networking", "networking"), new Tag("Vault", "vault"), new Tag("DNS", "dns")));

        BlockSuggestionResponse response =
                handler.apply(request("ticket-change-tags", "net"), mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).containsExactly("networking");
    }

    @Test
    void query_filtersByLabelCaseInsensitive_uppercaseQuery() {
        when(tagsRegistry.listAllTags())
                .thenReturn(ImmutableList.of(
                        new Tag("Networking", "networking"), new Tag("Vault", "vault"), new Tag("DNS", "dns")));

        BlockSuggestionResponse response =
                handler.apply(request("ticket-change-tags", "NET"), mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).containsExactly("networking");
    }

    @Test
    void nullQuery_treatedAsEmptyQuery_returnsAllTags() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Vault", "vault")));

        // Build request manually so we can stub getValue() as null (Slack SDK sends null on first open)
        BlockSuggestionRequest req = mock(BlockSuggestionRequest.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(req.getPayload().getActionId()).thenReturn("ticket-change-tags");
        when(req.getPayload().getValue()).thenReturn(null);

        BlockSuggestionResponse response = handler.apply(req, mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).containsExactly("vault");
    }

    @Test
    void query_filtersNoTagsOption_whenLabelDoesNotMatch() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Networking", "networking")));

        BlockSuggestionResponse response =
                handler.apply(request("homepage-filter-tags", "net"), mock(BlockSuggestionContext.class));

        // "net" does not match "-- No Tags --", so the special option should be absent
        assertThat(optionValues(response)).doesNotContain(HomepageFilterMapper.NO_TAGS_VALUE);
        assertThat(optionValues(response)).containsExactly("networking");
    }

    @Test
    void query_includesNoTagsOption_whenLabelMatches() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Networking", "networking")));

        BlockSuggestionResponse response =
                handler.apply(request("homepage-filter-tags", "no tag"), mock(BlockSuggestionContext.class));

        assertThat(optionValues(response)).contains(HomepageFilterMapper.NO_TAGS_VALUE);
    }

    @Test
    void options_limitedTo100() {
        ImmutableList<Tag> manyTags = ImmutableList.copyOf(IntStream.range(0, 150)
                .mapToObj(i -> new Tag("Tag " + i, "tag-" + i))
                .toList());
        when(tagsRegistry.listAllTags()).thenReturn(manyTags);

        BlockSuggestionResponse response =
                handler.apply(request("ticket-change-tags", ""), mock(BlockSuggestionContext.class));

        assertThat(response.getOptions()).hasSize(100);
    }

    @Test
    void noTagsOption_countsTowardsLimit_forHomepageFilter() {
        ImmutableList<Tag> manyTags = ImmutableList.copyOf(IntStream.range(0, 100)
                .mapToObj(i -> new Tag("Tag " + i, "tag-" + i))
                .toList());
        when(tagsRegistry.listAllTags()).thenReturn(manyTags);

        BlockSuggestionResponse response =
                handler.apply(request("homepage-filter-tags", ""), mock(BlockSuggestionContext.class));

        assertThat(response.getOptions()).hasSize(100);
        assertThat(optionValues(response).get(0)).isEqualTo(HomepageFilterMapper.NO_TAGS_VALUE);
    }

    private BlockSuggestionRequest request(String actionId, String value) {
        BlockSuggestionRequest req = mock(BlockSuggestionRequest.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(req.getPayload().getActionId()).thenReturn(actionId);
        when(req.getPayload().getValue()).thenReturn(value);
        return req;
    }

    private List<String> optionValues(BlockSuggestionResponse response) {
        return response.getOptions().stream().map(Option::getValue).toList();
    }

    private List<String> optionLabels(BlockSuggestionResponse response) {
        return response.getOptions().stream().map(o -> o.getText().getText()).toList();
    }
}
