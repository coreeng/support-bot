package com.coreeng.supportbot.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.JsonMapper;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.CheckboxesElement;
import com.slack.api.model.block.element.PlainTextInputElement;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RatingFeedbackViewMapperTest {
    private static final UUID RATING_ID = UUID.randomUUID();
    private static final TicketId TICKET_ID = new TicketId(7);

    private final RatingFeedbackViewMapper mapper = new RatingFeedbackViewMapper(new JsonMapper());

    @Test
    void low_rating_asks_what_went_wrong() {
        View view = render(2);

        assertThat(view.getCallbackId()).isEqualTo(RatingFeedbackViewMapper.CALLBACK_ID);
        assertThat(inputLabels(view)).containsExactly("What went wrong with your experience?", "Share your name?");
    }

    @Test
    void high_rating_asks_what_went_well() {
        View view = render(5);

        assertThat(inputLabels(view)).containsExactly("What made your experience worthwhile?", "Share your name?");
    }

    @Test
    void three_stars_is_treated_as_negative_and_four_as_positive() {
        assertThat(inputLabels(render(3))).first().isEqualTo("What went wrong with your experience?");
        assertThat(inputLabels(render(4))).first().isEqualTo("What made your experience worthwhile?");
    }

    @Test
    void share_name_is_optional_and_unticked_by_default() {
        InputBlock shareName = inputBlock(render(5), "rating-feedback-share-name");

        assertThat(shareName.isOptional()).isTrue();
        assertThat(((CheckboxesElement) shareName.getElement()).getInitialOptions())
                .isNullOrEmpty();
    }

    @Test
    void feedback_input_is_the_error_target_and_capped_at_the_slack_limit() {
        InputBlock feedback = inputBlock(render(2), mapper.feedbackBlockId());

        assertThat(feedback.isOptional()).isFalse();
        assertThat(((PlainTextInputElement) feedback.getElement()).getMaxLength())
                .isEqualTo(RatingService.FEEDBACK_MAX_LENGTH);
        assertThat(feedback.getHint().getText()).contains(String.valueOf(RatingService.FEEDBACK_MAX_LENGTH));
    }

    @Test
    void private_metadata_round_trips() {
        View view = render(3);

        assertThat(mapper.parseViewInput(view.getPrivateMetadata()))
                .isEqualTo(new RatingFeedbackViewInput(RATING_ID, TICKET_ID, 3));
    }

    @Test
    void extracts_all_submitted_values() {
        View view = render(5);
        view.setState(state(Map.of(
                "rating-feedback-text", textValue("Super quick"),
                "rating-feedback-share-name", optionsValue("share-name"))));

        RatingFeedbackSubmission submission = mapper.extractSubmittedValues(view);

        assertThat(submission).isEqualTo(new RatingFeedbackSubmission(RATING_ID, TICKET_ID, "Super quick", true));
    }

    @Test
    void unticked_share_name_means_anonymous() {
        View view = render(1);
        view.setState(state(Map.of(
                "rating-feedback-text", textValue("Nobody answered"),
                "rating-feedback-share-name", optionsValue())));

        RatingFeedbackSubmission submission = mapper.extractSubmittedValues(view);

        assertThat(submission.shareName()).isFalse();
        assertThat(submission.feedback()).isEqualTo("Nobody answered");
    }

    private View render(int rating) {
        return mapper.render(new RatingFeedbackViewInput(RATING_ID, TICKET_ID, rating), View.builder())
                .build();
    }

    private static InputBlock inputBlock(View view, String blockId) {
        return view.getBlocks().stream()
                .filter(InputBlock.class::isInstance)
                .map(InputBlock.class::cast)
                .filter(b -> blockId.equals(b.getBlockId()))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> inputLabels(View view) {
        return view.getBlocks().stream()
                .filter(InputBlock.class::isInstance)
                .map(b -> ((PlainTextObject) ((InputBlock) b).getLabel()).getText())
                .toList();
    }

    private static ViewState state(Map<String, ViewState.Value> valuesByActionId) {
        ViewState state = new ViewState();
        HashMap<String, Map<String, ViewState.Value>> values = new HashMap<>();
        // block id == action id in this modal
        valuesByActionId.forEach((id, value) -> values.put(id, Map.of(id, value)));
        state.setValues(values);
        return state;
    }

    private static ViewState.Value textValue(String text) {
        ViewState.Value value = new ViewState.Value();
        value.setValue(text);
        return value;
    }

    private static ViewState.Value optionsValue(String... optionValues) {
        ViewState.Value value = new ViewState.Value();
        value.setSelectedOptions(Arrays.stream(optionValues)
                .map(v -> {
                    ViewState.SelectedOption option = new ViewState.SelectedOption();
                    option.setValue(v);
                    return option;
                })
                .toList());
        return value;
    }
}
