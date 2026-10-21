package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.RatingFeedbackForm;
import com.coreeng.supportbot.testkit.RatingFeedbackFormSubmission;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import com.coreeng.supportbot.testkit.Ticket;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
public class RatingFeedbackTests {
    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @Test
    public void whenLowRatingWithAnonymousFeedback_feedbackIsNotLinkedToTicket() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        Ticket ticket = asTenant.ticket().create(builder -> builder.message("Rating feedback: low"));
        closeTicket(ticket);

        // when
        String triggerId = "rating_low_" + ticket.id();
        Ticket.RatingFlowStubs ratingStubs = ticket.stubRatingFlow("rated 2 stars", triggerId, 2);
        asTenant.slack().clickRatingButton(ticket.ratingButtonClick(triggerId, 2));
        ratingStubs.awaitAllCalled(Duration.ofSeconds(5));

        RatingFeedbackForm form = ratingStubs.formOpened().result();
        assertThat(form.inputLabels())
                .containsExactly("What went wrong with your experience?", "Link this feedback to your thread?");

        String feedback = "Slow first response " + UUID.randomUUID();
        asTenant.slack()
                .submitView(RatingFeedbackFormSubmission.builder()
                        .triggerId(triggerId)
                        .form(form)
                        .feedback(feedback)
                        .shareName(false)
                        .build());

        // then
        SupportBotClient.RatingFeedbackItem item = findFeedback(feedback);
        assertThat(item.rating()).isEqualTo(2);
        assertThat(item.ticketId()).isNull();
    }

    @Test
    public void whenHighRatingWithNamedFeedback_feedbackIsLinkedToTicket() {
        // given
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        Ticket ticket = asTenant.ticket().create(builder -> builder.message("Rating feedback: high"));
        closeTicket(ticket);

        // when
        String triggerId = "rating_high_" + ticket.id();
        Ticket.RatingFlowStubs ratingStubs = ticket.stubRatingFlow("rated 5 stars", triggerId, 5);
        asTenant.slack().clickRatingButton(ticket.ratingButtonClick(triggerId, 5));
        ratingStubs.awaitAllCalled(Duration.ofSeconds(5));

        RatingFeedbackForm form = ratingStubs.formOpened().result();
        assertThat(form.inputLabels())
                .containsExactly("What made your experience worthwhile?", "Link this feedback to your thread?");

        String feedback = "Fixed in minutes " + UUID.randomUUID();
        asTenant.slack()
                .submitView(RatingFeedbackFormSubmission.builder()
                        .triggerId(triggerId)
                        .form(form)
                        .feedback(feedback)
                        .shareName(true)
                        .build());

        // then
        SupportBotClient.RatingFeedbackItem item = findFeedback(feedback);
        assertThat(item.rating()).isEqualTo(5);
        assertThat(item.ticketId()).isEqualTo(ticket.id());
    }

    @Test
    public void ratingFeedback_isForbiddenForNonViewerRoles() {
        assertThat(supportBotClient.getStatusCodeAsRole("/rating/feedback", "escalation"))
                .isEqualTo(403);
    }

    private void closeTicket(Ticket ticket) {
        var closeStubs = ticket.stubCloseFlow("ticket closed");
        supportBotClient.updateTicket(
                ticket.id(),
                SupportBotClient.UpdateTicketRequest.builder()
                        .status("closed")
                        .authorsTeam("wow")
                        .tags(ImmutableList.of("ingresses"))
                        .impact("productionBlocking")
                        .build());
        closeStubs.awaitAllCalled(Duration.ofSeconds(5));
    }

    private SupportBotClient.RatingFeedbackItem findFeedback(String feedback) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return supportBotClient.ratingFeedback(today.minusDays(1), today.plusDays(1)).stream()
                .filter(f -> f.feedback().equals(feedback))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Feedback not returned by /rating/feedback: " + feedback));
    }
}
