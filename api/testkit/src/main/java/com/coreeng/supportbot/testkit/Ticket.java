package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class Ticket {
    private final long id;

    @NonNull private final MessageTs queryTs;

    @NonNull private final MessageTs formMessageTs;

    @NonNull private final String channelId;

    @NonNull private final SlackWiremock slackWiremock;

    private final SupportBotClient supportBotClient;
    private final Config.@NonNull User user;

    @NonNull private final Config config;

    @NonNull private final String teamId;

    @NonNull private final String queryBlocksJson;

    @NonNull @Builder.Default
    private final String queryText = "";

    @NonNull private final String queryPermalink;

    private Ticket.@NonNull Status status;
    private String team;
    private String impact;

    @NonNull @Builder.Default
    private ImmutableList<@NonNull String> tags = ImmutableList.of();

    @NonNull @Builder.Default
    private ImmutableList<@NonNull StatusLog> logs = ImmutableList.of();

    @Builder.Default
    private boolean escalated = false;

    @Builder.Default
    private ImmutableList<@NonNull EscalationInfo> escalations = ImmutableList.of();

    private String assignedTo;

    public static TicketBuilder fromResponse(SupportBotClient.TicketResponse ticketResponse) {
        return Ticket.builder()
                .id(ticketResponse.id())
                .queryTs(ticketResponse.query().ts())
                .formMessageTs(ticketResponse.formMessage().ts())
                .channelId(ticketResponse.channelId())
                .queryText(
                        ticketResponse.query().text() != null
                                ? ticketResponse.query().text()
                                : "")
                .status(Ticket.Status.fromCode(ticketResponse.status()))
                .team(ticketResponse.team() != null ? ticketResponse.team().code() : null)
                .tags(ticketResponse.tags())
                .logs(ticketResponse.logs())
                .assignedTo(ticketResponse.assignedTo());
    }

    public FullSummaryButtonClick fullSummaryButtonClick(String triggerId) {
        return FullSummaryButtonClick.builder()
                .triggerId(triggerId)
                .ticketId(id)
                .build();
    }

    public FullSummaryFormOpenStubs expectFullSummaryFormOpened(String reason, String triggerId) {
        var formOpenStub = slackWiremock.stubViewsOpen(ViewsOpenExpectation.<FullSummaryForm>builder()
                .description(reason + ": view open message")
                .viewCallbackId(FullSummaryFormSubmission.CALLBACK_ID)
                .viewType("modal")
                .triggerId(triggerId)
                .receiver(new FullSummaryForm.Receiver(this, config))
                .build());

        var queryPermalinkStub = slackWiremock.stubGetPermalink(reason + ": get query permalink", channelId, queryTs);
        var queryMessageStub = stubQueryMessageFetch(reason + ": get query message");

        return new FullSummaryFormOpenStubs(formOpenStub, queryPermalinkStub, queryMessageStub);
    }

    public FullSummaryFormSubmission fullSummaryFormSubmit(String triggerId, FullSummaryFormSubmission.Values values) {
        return FullSummaryFormSubmission.builder()
                .triggerId(triggerId)
                .ticketId(id)
                .values(values)
                .build();
    }

    public TeamSuggestionRequest teamSuggestionRequest() {
        return TeamSuggestionRequest.builder()
                .ticketId(id)
                .userId(user.slackUserId())
                .botId(user.slackBotId())
                .build();
    }

    public TeamSuggestionRequest teamSuggestionRequest(String filterValue) {
        return TeamSuggestionRequest.builder()
                .ticketId(id)
                .userId(user.slackUserId())
                .botId(user.slackBotId())
                .filterValue(filterValue)
                .build();
    }

    public TicketUpdater applyChangesLocally() {
        return new TicketUpdater();
    }

    public CloseFlowStubs stubCloseFlow(String reason) {
        StubWithResult<TicketMessage> messageUpdatedStub =
                slackWiremock.stubMessageUpdated(MessageUpdatedExpectation.<TicketMessage>builder()
                        .description(reason + ": ticket form update")
                        .channelId(channelId)
                        .ts(formMessageTs)
                        .threadTs(queryTs)
                        .receiver(new TicketMessage.Receiver())
                        .build());
        Stub messageCheckedStub = slackWiremock.stubReactionAdd(ReactionAddedExpectation.builder()
                .description(reason + ": checkmark added")
                .reaction("white_check_mark")
                .channelId(channelId)
                .ts(queryTs)
                .build());
        // Stub the ephemeral rating request message
        StubWithResult<RatingRequestMessage> ratingRequestStub =
                slackWiremock.stubEphemeralMessagePosted(EphemeralMessageExpectation.<RatingRequestMessage>builder()
                        .description(reason + ": rating request posted")
                        .channelId(channelId)
                        .threadTs(queryTs)
                        .userId(user.slackUserId())
                        .receiver(new RatingRequestMessage.Receiver(id))
                        .build());
        // Stub getting the original query message (needed for rating request to find user ID)
        Stub getQueryMessageStub = stubQueryMessageFetch(reason + ": get query message");
        return new CloseFlowStubs(messageUpdatedStub, messageCheckedStub, ratingRequestStub, getQueryMessageStub);
    }

    public ReopenFlowStubs stubReopenFlow(String reason) {
        StubWithResult<TicketMessage> updated =
                slackWiremock.stubMessageUpdated(MessageUpdatedExpectation.<TicketMessage>builder()
                        .description(reason + ": ticket form update")
                        .channelId(channelId)
                        .ts(formMessageTs)
                        .threadTs(queryTs)
                        .receiver(new TicketMessage.Receiver())
                        .build());
        Stub uncheck = slackWiremock.stubReactionRemove(ReactionAddedExpectation.builder()
                .description(reason + ": checkmark removed")
                .reaction("white_check_mark")
                .channelId(channelId)
                .ts(queryTs)
                .build());
        return new ReopenFlowStubs(updated, uncheck);
    }

    private Stub stubQueryMessageFetch(String description) {
        return slackWiremock.stubGetMessage(MessageToGet.builder()
                .description(description)
                .channelId(channelId)
                .ts(queryTs)
                .threadTs(queryTs)
                .text(queryText)
                .blocksJson(queryBlocksJson)
                .userId(user.slackUserId())
                .botId(user.slackBotId())
                .build());
    }

    public void openSummaryAndSubmit(
            SlackTestKit asSupportSlack, String reason, String triggerId, FullSummaryFormSubmission.Values values) {
        openSummaryAndSubmit(asSupportSlack, reason, triggerId, values, () -> null);
    }

    public <T> T openSummaryAndSubmit(
            SlackTestKit asSupportSlack,
            String reason,
            String triggerId,
            FullSummaryFormSubmission.Values values,
            Supplier<T> flowStubs) {
        FullSummaryFormOpenStubs openedStubs = expectFullSummaryFormOpened(reason, triggerId);
        asSupportSlack.clickMessageButton(fullSummaryButtonClick(triggerId));
        openedStubs.awaitAllCalled(Duration.ofSeconds(5));
        T stubs = flowStubs.get();
        asSupportSlack.submitView(fullSummaryFormSubmit(triggerId, values));
        return stubs;
    }

    public EscalateFlowStubs stubEscalateFlow(
            String reason, String expectedSlackGroupId, MessageTs newEscalationMessageTs) {
        StubWithResult<EscalationMessage> escalationMessage =
                expectEscalationMessagePosted(reason, expectedSlackGroupId, newEscalationMessageTs);
        Stub rocketReactionAdded = slackWiremock.stubReactionAdd(ReactionAddedExpectation.builder()
                .description(reason + ": rocket reaction added")
                .reaction("rocket")
                .channelId(channelId)
                .ts(queryTs)
                .build());
        return new EscalateFlowStubs(escalationMessage, rocketReactionAdded);
    }

    public void openEscalationAndSubmit(
            SlackTestKit asSupportSlack, String reason, String triggerId, EscalationFormSubmission.Values values) {
        StubWithResult<EscalationForm> opened = expectEscalationFormOpened(reason, triggerId);
        asSupportSlack.clickMessageButton(escalateButtonClick(triggerId));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(opened::assertIsCalled);
        asSupportSlack.submitView(escalationFormSubmit(triggerId, values));
    }

    public void assertMatches(SupportBotClient.TicketResponse response) {
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.query().ts()).isEqualTo(queryTs);
        if (response.query().text() != null) {
            assertThat(response.query().text()).isEqualTo(queryText);
        }
        assertThat(response.formMessage().ts()).isEqualTo(formMessageTs);
        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(status.code());
        if (team == null) {
            assertThat(response.team()).isNull();
        } else {
            assertThat(response.team().code()).isEqualTo(team);
        }
        assertThat(response.tags()).isEqualTo(tags());
        assertThat(response.assignedTo()).isEqualTo(assignedTo);

        assertThat(response.escalated()).isEqualTo(escalated);
        assertThat(response.escalations()).hasSize(escalations.size());
        for (int i = 0; i < escalations.size(); i++) {
            var expectedEscalation = escalations.get(i);
            var actualEscalation = response.escalations().get(i);
            assertThat(actualEscalation.team().code()).isEqualTo(expectedEscalation.team());
            assertThat(actualEscalation.tags()).isEqualTo(expectedEscalation.tags());
            if (expectedEscalation.createdAt() != null) {
                assertThat(actualEscalation.openedAt())
                        .isCloseTo(expectedEscalation.createdAt(), within(2, ChronoUnit.SECONDS));
            }
            if (expectedEscalation.resolvedAt() != null) {
                assertThat(actualEscalation.resolvedAt())
                        .isCloseTo(expectedEscalation.resolvedAt(), within(2, ChronoUnit.SECONDS));
            }
        }

        assertThat(response.logs()).hasSize(logs.size());
        for (int i = 0; i < response.logs().size(); i++) {
            var expectedLog = logs.get(i);
            var actualLog = response.logs().get(i);
            assertThat(actualLog.event()).isEqualTo(expectedLog.event());
            assertThat(actualLog.date()).isCloseTo(expectedLog.date(), within(1, ChronoUnit.SECONDS));
        }
    }

    public EscalationButtonClick escalateButtonClick(String triggerId) {
        return EscalationButtonClick.builder().triggerId(triggerId).ticket(this).build();
    }

    public StubWithResult<EscalationForm> expectEscalationFormOpened(String reason, String triggerId) {
        var expectation = ViewsOpenExpectation.<EscalationForm>builder()
                .description(reason + ": escalation form opened")
                .viewCallbackId("ticket-escalate")
                .viewType("modal")
                .triggerId(triggerId)
                .receiver(new EscalationForm.Receiver(this))
                .build();
        return slackWiremock.stubViewsOpen(expectation);
    }

    public EscalationFormSubmission escalationFormSubmit(String triggerId, EscalationFormSubmission.Values values) {
        return EscalationFormSubmission.builder()
                .triggerId(triggerId)
                .ticketId(id)
                .values(values)
                .build();
    }

    public StubWithResult<EscalationMessage> expectEscalationMessagePosted(
            String reason, String expectedSlackGroupId, MessageTs ts) {
        return slackWiremock.stubMessagePosted(ThreadMessagePostedExpectation.<EscalationMessage>builder()
                .description(reason + ": escalation message posted")
                .receiver(new EscalationMessage.Receiver(expectedSlackGroupId))
                .from(UserRole.supportBot)
                .newMessageTs(ts)
                .channelId(channelId)
                .threadTs(queryTs)
                .build());
    }

    public void escalateViaTestApi(MessageTs ts, String team, ImmutableList<@NonNull String> tags) {
        supportBotClient
                .test()
                .escalateTicket(SupportBotClient.EscalationToCreate.builder()
                        .ticketId(id)
                        .createdMessageTs(ts)
                        .team(team)
                        .tags(tags)
                        .build());
        escalated = true;
        escalations = ImmutableList.<EscalationInfo>builder()
                .addAll(escalations)
                .add(new EscalationInfo(team, tags, Instant.now(), null))
                .build();
    }

    @Getter
    public enum Status {
        opened("opened", "Opened", "#00ff00", "large_orange_circle"),
        closed("closed", "Closed", "#ff000d", "large_green_circle");

        private final String code;
        private final String label;
        private final String colorHex;
        private final String emojiName;

        Status(String code, String label, String colorHex, String emojiName) {
            this.code = code;
            this.label = label;
            this.colorHex = colorHex;
            this.emojiName = emojiName;
        }

        public static Status fromCode(String code) {
            for (Status s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown ticket status code: " + code);
        }

        public static Status fromColor(String colorHex) {
            for (Status s : values()) {
                if (s.colorHex.equalsIgnoreCase(colorHex)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown ticket status color: " + colorHex);
        }
    }

    record StatusLog(String event, Instant date) {}

    public record EscalationInfo(
            String team, ImmutableList<@NonNull String> tags, Instant createdAt, Instant resolvedAt) {}

    public class TicketUpdater {
        public TicketUpdater applyFormValues(FullSummaryFormSubmission.Values values) {
            status = values.status();
            team = values.team();
            tags = values.tags();
            impact = values.impact();
            assignedTo = values.assignedTo();
            return this;
        }

        public TicketUpdater addLog(String status) {
            logs = ImmutableList.<StatusLog>builder()
                    .addAll(logs)
                    .add(new StatusLog(status, Instant.now()))
                    .build();
            return this;
        }

        public TicketUpdater applyEscalationFromValues(EscalationFormSubmission.Values values) {
            escalated = true;
            escalations = ImmutableList.<EscalationInfo>builder()
                    .addAll(escalations)
                    .add(new EscalationInfo(values.team(), values.tags(), Instant.now(), null))
                    .build();
            return this;
        }

        public TicketUpdater resolveEscalations() {
            escalated = false;
            escalations = escalations.stream()
                    .map(e -> new EscalationInfo(e.team(), e.tags(), e.createdAt(), Instant.now()))
                    .collect(ImmutableList.toImmutableList());
            return this;
        }
    }

    public record FullSummaryFormOpenStubs(
            StubWithResult<FullSummaryForm> formOpened, Stub queryPermalink, Stub getQueryMessage) {
        public void awaitAllCalled(Duration timeout) {
            await().atMost(timeout).untilAsserted(() -> {
                queryPermalink.cleanUp(); // permalink call is cached, so it's not necessary called
                getQueryMessage.assertIsCalled();
                formOpened.assertIsCalled();
            });
        }
    }

    public record CloseFlowStubs(
            StubWithResult<TicketMessage> messageUpdated,
            Stub whiteCheckMarkAdded,
            StubWithResult<RatingRequestMessage> ratingRequestPosted,
            Stub getQueryMessage) {
        public void awaitAllCalled(Duration timeout) {
            await().atMost(timeout).untilAsserted(() -> {
                messageUpdated.assertIsCalled();
                whiteCheckMarkAdded.assertIsCalled();
                ratingRequestPosted.assertIsCalled();
                getQueryMessage.assertIsCalled();
            });
        }
    }

    public record ReopenFlowStubs(StubWithResult<TicketMessage> messageUpdated, Stub whiteCheckMarkRemoved) {
        public void awaitAllCalled(Duration timeout) {
            await().atMost(timeout).untilAsserted(() -> {
                messageUpdated.assertIsCalled();
                whiteCheckMarkRemoved.assertIsCalled();
            });
        }
    }

    public record EscalateFlowStubs(StubWithResult<EscalationMessage> escalationMessage, Stub rocketReactionAdded) {
        public void awaitAllCalled(Duration timeout) {
            await().atMost(timeout).untilAsserted(() -> {
                escalationMessage.assertIsCalled();
                rocketReactionAdded.assertIsCalled();
            });
        }
    }
}
