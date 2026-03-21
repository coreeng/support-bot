package com.coreeng.supportbot.enums;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

import com.coreeng.supportbot.slack.SlackBlockSuggestionHandler;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.interactive_components.response.BlockSuggestionResponse;
import com.slack.api.app_backend.interactive_components.response.Option;
import com.slack.api.bolt.context.builtin.BlockSuggestionContext;
import com.slack.api.bolt.request.builtin.BlockSuggestionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagSuggestionHandler implements SlackBlockSuggestionHandler {

    /**
     * Action ID for the homepage filter tags field (HomepageFilterMapper.FilterField.tags).
     * Used to decide whether to prepend the special "-- No Tags --" option.
     */
    private static final String HOMEPAGE_FILTER_TAGS_ACTION_ID = "homepage-filter-tags";

    /**
     * Special option prepended when the action ID is {@code HOMEPAGE_FILTER_TAGS_ACTION_ID},
     * allowing users to find tickets that have no tags at all.
     * Must stay in sync with HomepageFilterMapper.NO_TAGS_VALUE / NO_TAGS_LABEL.
     */
    static final String NO_TAGS_VALUE = "__no_tags__";

    static final String NO_TAGS_LABEL = "-- No Tags --";

    private static final int SLACK_OPTIONS_LIMIT = 100;

    /**
     * Matches all action IDs that use the external tag select element.
     * If a new tag field is added, extend this pattern and add a case to {@code TagSuggestionHandlerTest}.
     */
    private static final Pattern ACTION_ID_PATTERN =
            Pattern.compile("^(homepage-filter-tags|escalation-tags|ticket-change-tags)$");

    private final TagsRegistry tagsRegistry;

    @Override
    public Pattern getPattern() {
        return ACTION_ID_PATTERN;
    }

    @Override
    public BlockSuggestionResponse apply(BlockSuggestionRequest req, BlockSuggestionContext ctx) {
        var payload = checkNotNull(req.getPayload(), "BlockSuggestionRequest payload must not be null");
        String actionId = payload.getActionId();
        String query = payload.getValue();
        String lowerQuery = query != null ? query.toLowerCase(Locale.ROOT) : "";

        List<Option> options = new ArrayList<>();

        if (HOMEPAGE_FILTER_TAGS_ACTION_ID.equals(actionId)
                && NO_TAGS_LABEL.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            options.add(Option.builder()
                    .text(plainText(NO_TAGS_LABEL))
                    .value(NO_TAGS_VALUE)
                    .build());
        }

        // Reserve slots already used by special options (e.g. NO_TAGS) so total never exceeds the limit.
        ImmutableList<Option> tagOptions = tagsRegistry.listAllTags().stream()
                .filter(tag -> lowerQuery.isBlank()
                        || tag.label().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .limit(SLACK_OPTIONS_LIMIT - options.size())
                .map(tag -> Option.builder()
                        .text(plainText(tag.label()))
                        .value(tag.code())
                        .build())
                .collect(toImmutableList());
        options.addAll(tagOptions);

        return BlockSuggestionResponse.builder().options(options).build();
    }
}
