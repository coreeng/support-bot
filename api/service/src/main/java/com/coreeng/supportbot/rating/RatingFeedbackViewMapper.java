package com.coreeng.supportbot.rating;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.slack.api.model.block.Blocks.context;
import static com.slack.api.model.block.Blocks.input;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.checkboxes;
import static com.slack.api.model.block.element.BlockElements.plainTextInput;
import static com.slack.api.model.view.Views.viewClose;
import static com.slack.api.model.view.Views.viewSubmit;
import static com.slack.api.model.view.Views.viewTitle;

import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RatingFeedbackViewMapper {
    public static final String CALLBACK_ID = "rating-feedback";

    private static final String SHARE_NAME_VALUE = "share-name";

    private final JsonMapper jsonMapper;

    public View.ViewBuilder render(RatingFeedbackViewInput input, View.ViewBuilder view) {
        return view.callbackId(CALLBACK_ID)
                .type("modal")
                .title(viewTitle(t -> t.type("plain_text").text("Tell us more")))
                .submit(viewSubmit(s -> s.type("plain_text").text("Send feedback")))
                .close(viewClose(c -> c.type("plain_text").text("Skip")))
                .privateMetadata(jsonMapper.toJsonString(input))
                .blocks(renderBlocks(input));
    }

    private List<LayoutBlock> renderBlocks(RatingFeedbackViewInput input) {
        ImmutableList.Builder<LayoutBlock> blocks = ImmutableList.builder();
        blocks.add(section(s -> s.text(markdownText(introText(input)))));
        blocks.add(input(i -> i.blockId(Fields.feedback.id())
                .label(plainText(
                        input.isPositive()
                                ? "What made your experience worthwhile?"
                                : "What went wrong with your experience?"))
                .hint(plainText("Up to " + RatingService.FEEDBACK_MAX_LENGTH + " characters"))
                .element(plainTextInput(p -> p.actionId(Fields.feedback.id())
                        .multiline(true)
                        .maxLength(RatingService.FEEDBACK_MAX_LENGTH)))));

        blocks.add(input(i -> i.blockId(Fields.shareName.id())
                .optional(true)
                .label(plainText("Share your name?"))
                .element(checkboxes(c -> c.actionId(Fields.shareName.id())
                        .options(ImmutableList.of(option(
                                "Share my name with this feedback",
                                SHARE_NAME_VALUE,
                                "Links your feedback to this support thread so we can follow up.")))))));
        blocks.add(context(c -> c.elements(ImmutableList.of(markdownText(
                "Leave this unticked to stay anonymous: your feedback won't be linked to you or this thread.")))));
        return blocks.build();
    }

    private static String introText(RatingFeedbackViewInput input) {
        String stars = "⭐".repeat(input.rating());
        return input.isPositive()
                ? "Thanks for rating us " + stars + "! We'd love to hear what went well."
                : "Thanks for rating us " + stars + ". We're sorry it wasn't a great experience,"
                        + " please help us understand what went wrong.";
    }

    private static OptionObject option(String text, String value, String description) {
        return OptionObject.builder()
                .text(plainText(text))
                .value(value)
                .description(plainText(description))
                .build();
    }

    public RatingFeedbackViewInput parseViewInput(String privateMetadata) {
        return jsonMapper.fromJsonString(privateMetadata, RatingFeedbackViewInput.class);
    }

    public RatingFeedbackSubmission extractSubmittedValues(View view) {
        checkNotNull(view);
        RatingFeedbackViewInput input = parseViewInput(view.getPrivateMetadata());
        ImmutableMap<String, ViewState.Value> values = view.getState().getValues().entrySet().stream()
                .flatMap(entry -> entry.getValue().entrySet().stream())
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        ViewState.Value feedbackValue = values.get(Fields.feedback.id());
        String feedback = feedbackValue != null && feedbackValue.getValue() != null ? feedbackValue.getValue() : "";

        return new RatingFeedbackSubmission(
                input.ratingId(),
                input.ticketId(),
                feedback,
                isTicked(values.get(Fields.shareName.id()), SHARE_NAME_VALUE));
    }

    private static boolean isTicked(ViewState.@Nullable Value value, String optionValue) {
        return value != null
                && value.getSelectedOptions() != null
                && value.getSelectedOptions().stream().anyMatch(o -> optionValue.equals(o.getValue()));
    }

    public String feedbackBlockId() {
        return Fields.feedback.id();
    }

    // Block id == action id, so validation errors can target the right block
    @Getter
    @RequiredArgsConstructor
    private enum Fields {
        feedback("rating-feedback-text"),
        shareName("rating-feedback-share-name");

        private final String id;
    }
}
