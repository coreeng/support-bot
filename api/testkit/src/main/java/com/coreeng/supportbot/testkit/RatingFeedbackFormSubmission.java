package com.coreeng.supportbot.testkit;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class RatingFeedbackFormSubmission implements ViewSubmission {
    public static final String CALLBACK_ID = "rating-feedback";

    @NonNull private final String triggerId;

    @NonNull private final RatingFeedbackForm form;

    @NonNull private final String feedback;

    private final boolean shareName;

    @Override
    public String callbackId() {
        return CALLBACK_ID;
    }

    @Override
    public String viewType() {
        return "modal";
    }

    @Override
    public String privateMetadata() {
        return form.privateMetadata();
    }

    @Override
    public ImmutableList<Value> values() {
        return ImmutableList.of(
                new PlainTextValue("rating-feedback-text", feedback),
                new CheckboxesValue(
                        "rating-feedback-share-name", shareName ? ImmutableList.of("share-name") : ImmutableList.of()));
    }
}
