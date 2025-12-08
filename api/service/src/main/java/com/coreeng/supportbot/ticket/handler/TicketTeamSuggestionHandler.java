package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackBlockSuggestionHandler;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.ticket.TicketField;
import com.coreeng.supportbot.ticket.TicketSummaryView;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.ticket.TicketTeamSuggestionsService;
import com.coreeng.supportbot.ticket.TicketTeamsSuggestion;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.interactive_components.response.BlockSuggestionResponse;
import com.slack.api.app_backend.interactive_components.response.Option;
import com.slack.api.app_backend.interactive_components.response.OptionGroup;
import com.slack.api.bolt.context.builtin.BlockSuggestionContext;
import com.slack.api.bolt.request.builtin.BlockSuggestionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketTeamSuggestionHandler implements SlackBlockSuggestionHandler {
    private final static int slackOptionsLimit = 100;

    private final TicketTeamSuggestionsService service;
    private final TicketSummaryViewMapper viewMapper;

    @Override
    public Pattern getPattern() {
        return Pattern.compile("^" + TicketField.team.actionId() + "$");
    }

    @Override
    public BlockSuggestionResponse apply(BlockSuggestionRequest req, BlockSuggestionContext ctx) {
        String value = req.getPayload().getValue();
        TicketSummaryView.Metadata metadata = viewMapper.parseMetadata(req.getPayload().getView().getPrivateMetadata());
        SlackId authorId = metadata.authorId();

        try {
            var teamSuggestion = service.getTeamSuggestions(value, authorId);
            return renderTeamSuggestions(teamSuggestion);
        } catch (Exception e) {
            log.atError()
                .setCause(e)
                .addKeyValue("authorId", authorId != null ? authorId.id() : "null")
                .addKeyValue("ticketId", metadata.ticketId())
                .log("Error getting team suggestions, returning fallback with all teams");
            var fallbackSuggestion = service.getFallbackSuggestions(value);
            return renderTeamSuggestions(fallbackSuggestion);
        }
    }

    private BlockSuggestionResponse renderTeamSuggestions(TicketTeamsSuggestion teams) {
        ImmutableList.Builder<OptionGroup> optionGroupsBuilder = ImmutableList.builderWithExpectedSize(2);
        if (!teams.userTeams().isEmpty()) {
            optionGroupsBuilder.add(
                OptionGroup.builder()
                    .label(plainText("Suggested teams"))
                    .options(
                        teams.userTeams().stream()
                            .limit(slackOptionsLimit)
                            .map(t -> Option.builder()
                                .text(plainText(t))
                                .value(t)
                                .build())
                            .collect(toImmutableList())
                    )
                    .build()

            );
        }
        if (!teams.otherTeams().isEmpty()) {
            optionGroupsBuilder.add(
                OptionGroup.builder()
                    .label(plainText("Others"))
                    .options(
                        teams.otherTeams().stream()
                            .limit(slackOptionsLimit)
                            .map(t -> Option.builder()
                                .text(plainText(t))
                                .value(t)
                                .build())
                            .collect(toImmutableList())
                    )
                    .build()
            );
        }
        ImmutableList<OptionGroup> optionGroups = optionGroupsBuilder.build();
        return BlockSuggestionResponse.builder()
            .optionGroups(optionGroups)
            .build();
    }
}
