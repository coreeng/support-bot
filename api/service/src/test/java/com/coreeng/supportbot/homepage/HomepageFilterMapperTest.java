package com.coreeng.supportbot.homepage;

import static com.slack.api.model.view.Views.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.element.MultiExternalSelectElement;
import com.slack.api.model.view.View;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomepageFilterMapperTest {

    @Mock
    private TagsRegistry tagsRegistry;

    @Mock
    private ImpactsRegistry impactsRegistry;

    @Mock
    private EscalationTeamsRegistry escalationTeamsRegistry;

    @Mock
    private SupportTeamService supportTeamService;

    private HomepageFilterMapper mapper;

    @BeforeEach
    void setUp() {
        TicketAssignmentProps assignmentProps =
                new TicketAssignmentProps(false, new TicketAssignmentProps.Encryption(false, null));
        mapper = new HomepageFilterMapper(
                tagsRegistry, impactsRegistry, escalationTeamsRegistry, supportTeamService, assignmentProps);

        lenient().when(escalationTeamsRegistry.listAllEscalationTeams()).thenReturn(ImmutableList.of());
        lenient().when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of());
        lenient().when(tagsRegistry.listTagsByCodes(any())).thenReturn(ImmutableList.of());
    }

    @Test
    void initialTagOptions_onlyIncludesActiveTags_softDeletedTagsAreDropped() {
        // listTagsByCodes() only returns active tags — soft-deleted "old-tag" is absent
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("networking", "old-tag")))
                .thenReturn(ImmutableList.of(new Tag("Networking", "networking")));

        // Filter has both an active tag and a soft-deleted tag code from a previous session
        HomepageFilter filter = HomepageFilter.builder()
                .tags(ImmutableList.of("networking", "old-tag"))
                .build();

        View rendered = render(filter);

        List<String> initialOptionValues = extractTagsInitialOptionValues(rendered);
        assertThat(initialOptionValues).containsExactly("networking");
        assertThat(initialOptionValues).doesNotContain("old-tag");
    }

    @Test
    void initialTagOptions_isNull_whenFilterHasNoTags() {
        HomepageFilter filter = HomepageFilter.builder().build();

        View rendered = render(filter);

        MultiExternalSelectElement tagsElement = extractTagsElement(rendered);
        assertThat(tagsElement.getInitialOptions()).isNull();
    }

    @Test
    void initialTagOptions_includesBothActiveTags_whenAllFilterTagsAreActive() {
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("networking", "vault")))
                .thenReturn(ImmutableList.of(new Tag("Networking", "networking"), new Tag("Vault", "vault")));

        HomepageFilter filter = HomepageFilter.builder()
                .tags(ImmutableList.of("networking", "vault"))
                .build();

        View rendered = render(filter);

        List<String> values = extractTagsInitialOptionValues(rendered);
        assertThat(values).containsExactly("networking", "vault");
    }

    @Test
    void initialTagOptions_orderFollowsRepository_notFilterOrder() {
        // Repository returns tags in config order (vault before networking);
        // the filter listed them in the opposite order.
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("networking", "vault")))
                .thenReturn(ImmutableList.of(new Tag("Vault", "vault"), new Tag("Networking", "networking")));

        HomepageFilter filter = HomepageFilter.builder()
                .tags(ImmutableList.of("networking", "vault"))
                .build();

        View rendered = render(filter);

        List<String> values = extractTagsInitialOptionValues(rendered);
        assertThat(values).containsExactly("vault", "networking");
    }

    @Test
    void initialTagOptions_includesNoTagsOption_whenFilterHasIncludeNoTags() {
        HomepageFilter filter = HomepageFilter.builder().includeNoTags(true).build();

        View rendered = render(filter);

        List<String> values = extractTagsInitialOptionValues(rendered);
        assertThat(values).contains(HomepageFilterMapper.NO_TAGS_VALUE);
    }

    @Test
    void initialTagOptions_noTagsOptionFirst_thenActiveTags_whenBothSet() {
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("networking")))
                .thenReturn(ImmutableList.of(new Tag("Networking", "networking")));

        HomepageFilter filter = HomepageFilter.builder()
                .includeNoTags(true)
                .tags(ImmutableList.of("networking"))
                .build();

        View rendered = render(filter);

        List<String> values = extractTagsInitialOptionValues(rendered);
        assertThat(values.get(0)).isEqualTo(HomepageFilterMapper.NO_TAGS_VALUE);
        assertThat(values).containsExactly(HomepageFilterMapper.NO_TAGS_VALUE, "networking");
    }

    private View render(HomepageFilter filter) {
        HomepageView.State state = HomepageView.State.builder().filter(filter).build();
        return view(v -> mapper.render(state, v).type("modal"));
    }

    private MultiExternalSelectElement extractTagsElement(View view) {
        return view.getBlocks().stream()
                .filter(b -> b instanceof InputBlock)
                .map(b -> (InputBlock) b)
                .filter(b -> b.getElement() instanceof MultiExternalSelectElement e
                        && HomepageFilterMapper.FilterField.tags.actionId().equals(e.getActionId()))
                .map(b -> (MultiExternalSelectElement) b.getElement())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tags input block not found"));
    }

    private List<String> extractTagsInitialOptionValues(View view) {
        List<OptionObject> initialOptions = extractTagsElement(view).getInitialOptions();
        assertThat(initialOptions).isNotNull();
        return initialOptions.stream().map(OptionObject::getValue).toList();
    }
}
