package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.RatingFeedbackSubmission;
import com.coreeng.supportbot.rating.RatingFeedbackViewMapper;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persists the follow-up feedback modal.
 *
 * <p>No RBAC check: the modal is only ever opened for the user who clicked the rating, and its private metadata is
 * set by us, so the submission can't be redirected to another rating.
 */
@Component
@RequiredArgsConstructor
public class RatingFeedbackSubmissionHandler implements SlackViewSubmitHandler {
    private static final Pattern PATTERN = Pattern.compile("^" + RatingFeedbackViewMapper.CALLBACK_ID + "$");

    private final RatingFeedbackViewMapper feedbackViewMapper;
    private final RatingService ratingService;

    @Override
    public Pattern getPattern() {
        return PATTERN;
    }

    @Override
    public ViewSubmissionResponse apply(ViewSubmissionRequest request, ViewSubmissionContext context) {
        RatingFeedbackSubmission submission =
                feedbackViewMapper.extractSubmittedValues(request.getPayload().getView());
        if (submission.feedback().isBlank()) {
            return ViewSubmissionResponse.builder()
                    .responseAction("errors")
                    .errors(Map.of(
                            feedbackViewMapper.feedbackBlockId(), "Please tell us a little about your experience"))
                    .build();
        }
        ratingService.submitFeedback(submission);
        return new ViewSubmissionResponse();
    }
}
