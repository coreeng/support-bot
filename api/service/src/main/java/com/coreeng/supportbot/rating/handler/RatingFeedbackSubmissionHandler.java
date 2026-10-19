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
 * Validates and persists submissions of the follow-up feedback modal.
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
        String feedback = submission.feedback().strip();
        if (feedback.isEmpty()) {
            return fieldError("Please tell us a little about your experience");
        }
        // Slack enforces the same limit, but may count characters (e.g. emoji) differently from String.length()
        if (feedback.length() > RatingService.FEEDBACK_MAX_LENGTH) {
            return fieldError(
                    "Please keep your feedback to " + RatingService.FEEDBACK_MAX_LENGTH + " characters or fewer");
        }
        // A false result means feedback was already recorded for this rating. The first submission is kept and the
        // modal closes as normal, since there's nothing the submitter can do about it.
        ratingService.submitFeedback(submission);
        return new ViewSubmissionResponse();
    }

    private ViewSubmissionResponse fieldError(String message) {
        return ViewSubmissionResponse.builder()
                .responseAction("errors")
                .errors(Map.of(feedbackViewMapper.feedbackBlockId(), message))
                .build();
    }
}
