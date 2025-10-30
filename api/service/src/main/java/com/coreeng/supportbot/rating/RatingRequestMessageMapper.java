package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.rating.handler.RatingActionHandler;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackMessage;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

@Component
@RequiredArgsConstructor
public class RatingRequestMessageMapper {
    private final JsonMapper jsonMapper;

    public RatingButtonInput parseButtonInput(String value) {
        return jsonMapper.fromJsonString(value, RatingButtonInput.class);
    }

    public SlackMessage renderRatingRequestMessage(RatingRequestMessage message) {
        return SimpleSlackMessage.builder()
            .text("How was your support experience?")
            .blocks(ImmutableList.of(
                section(s -> s
                    .text(markdownText(
                        "*How was your support experience?* \n" +
                        "_Your feedback helps us improve our service. Ratings are collected anonymously to protect your privacy._"
                    ))
                ),
                actions(ImmutableList.of(
                    button(b -> b
                        // Slack requires action id to be unique across all actions
                        .actionId(RatingActionHandler.actionId(1))
                        .text(plainText("⭐"))
                        .value(jsonMapper.toJsonString(new RatingButtonInput(
                            message.ticketId(),
                            1
                        )))
                    ),
                    button(b -> b
                        .actionId(RatingActionHandler.actionId(2))
                        .text(plainText("⭐⭐"))
                        .value(jsonMapper.toJsonString(new RatingButtonInput(
                            message.ticketId(),
                            2
                        )))
                    ),
                    button(b -> b
                        .actionId(RatingActionHandler.actionId(3))
                        .text(plainText("⭐⭐⭐"))
                        .value(jsonMapper.toJsonString(new RatingButtonInput(
                            message.ticketId(),
                            3
                        )))
                    ),
                    button(b -> b
                        .actionId(RatingActionHandler.actionId(4))
                        .text(plainText("⭐⭐⭐⭐"))
                        .value(jsonMapper.toJsonString(new RatingButtonInput(
                            message.ticketId(),
                            4
                        )))
                    ),
                    button(b -> b
                        .actionId(RatingActionHandler.actionId(5))
                        .text(plainText("⭐⭐⭐⭐⭐"))
                        .value(jsonMapper.toJsonString(new RatingButtonInput(
                            message.ticketId(),
                            5
                        )))
                    )
                ))
            ))
            .build();
    }
}
