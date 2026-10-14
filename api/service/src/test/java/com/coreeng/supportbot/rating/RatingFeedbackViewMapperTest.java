package com.coreeng.supportbot.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.JsonMapper;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.composition.PlainTextObject;
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
    void high_rating_asks_what_went_well_without_asking_for_names() {
        View view = render(5);

        assertThat(inputLabels(view)).containsExactly("What made your experience worthwhile?", "Share your name?");
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
