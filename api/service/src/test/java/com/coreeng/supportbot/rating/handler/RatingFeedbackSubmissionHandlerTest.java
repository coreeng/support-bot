package com.coreeng.supportbot.rating.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.rating.RatingFeedbackSubmission;
import com.coreeng.supportbot.rating.RatingFeedbackViewMapper;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.ticket.TicketId;
import com.slack.api.app_backend.views.payload.ViewSubmissionPayload;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.model.view.View;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingFeedbackSubmissionHandlerTest {
    @Mock
    private RatingFeedbackViewMapper feedbackViewMapper;

    @Mock
    private RatingService ratingService;

    private RatingFeedbackSubmissionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RatingFeedbackSubmissionHandler(feedbackViewMapper, ratingService);
    }

    @Test
    void handles_only_feedback_callback() {
        assertThat(handler.getPattern()
                        .matcher(RatingFeedbackViewMapper.CALLBACK_ID)
                        .matches())
                .isTrue();
        assertThat(handler.getPattern().matcher("ticket-summary").matches()).isFalse();
    }

    @Test
    void saves_feedback_and_closes_modal() {
        View view = new View();
        RatingFeedbackSubmission submission = submission("Fast and friendly");
        when(feedbackViewMapper.extractSubmittedValues(view)).thenReturn(submission);

        ViewSubmissionResponse response = handler.apply(request(view), mock(ViewSubmissionContext.class));

        verify(ratingService).submitFeedback(submission);
        assertThat(response.getResponseAction()).isNull();
    }

    @Test
    void blank_feedback_returns_field_error() {
        View view = new View();
        when(feedbackViewMapper.extractSubmittedValues(view)).thenReturn(submission("  "));
        when(feedbackViewMapper.feedbackBlockId()).thenReturn("rating-feedback-text");

        ViewSubmissionResponse response = handler.apply(request(view), mock(ViewSubmissionContext.class));

        assertThat(response.getResponseAction()).isEqualTo("errors");
        assertThat(response.getErrors()).containsKey("rating-feedback-text");
        verify(ratingService, never()).submitFeedback(any());
    }

    @Test
    void too_long_feedback_returns_field_error() {
        View view = new View();
        when(feedbackViewMapper.extractSubmittedValues(view))
                .thenReturn(submission("x".repeat(RatingService.FEEDBACK_MAX_LENGTH + 1)));
        when(feedbackViewMapper.feedbackBlockId()).thenReturn("rating-feedback-text");

        ViewSubmissionResponse response = handler.apply(request(view), mock(ViewSubmissionContext.class));

        assertThat(response.getResponseAction()).isEqualTo("errors");
        assertThat(response.getErrors()).containsKey("rating-feedback-text");
        verify(ratingService, never()).submitFeedback(any());
    }

    @Test
    void duplicate_submission_still_closes_modal() {
        View view = new View();
        RatingFeedbackSubmission submission = submission("Second go");
        when(feedbackViewMapper.extractSubmittedValues(view)).thenReturn(submission);
        when(ratingService.submitFeedback(submission)).thenReturn(false);

        ViewSubmissionResponse response = handler.apply(request(view), mock(ViewSubmissionContext.class));

        assertThat(response.getResponseAction()).isNull();
    }

    private static RatingFeedbackSubmission submission(String feedback) {
        return new RatingFeedbackSubmission(UUID.randomUUID(), new TicketId(1), feedback, false);
    }

    private static ViewSubmissionRequest request(View view) {
        ViewSubmissionPayload payload = new ViewSubmissionPayload();
        payload.setView(view);
        ViewSubmissionRequest request = mock(ViewSubmissionRequest.class);
        when(request.getPayload()).thenReturn(payload);
        return request;
    }
}
