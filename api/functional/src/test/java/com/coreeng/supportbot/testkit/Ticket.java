package com.coreeng.supportbot.testkit;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import org.jspecify.annotations.NonNull;

import com.coreeng.supportbot.Config;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import com.google.common.collect.ImmutableList;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Ticket implements SearchableForTicket {
    private final long id;
    @NonNull
    private final MessageTs queryTs;
    @NonNull
    private final MessageTs formMessageTs;
    @NonNull
    private final String channelId;
    @NonNull
    private final SlackWiremock slackWiremock;
    private final SupportBotClient supportBotClient;
    private final Config.@NonNull User user;
    @NonNull
    private final Config config;
    @NonNull
    private final String teamId;
    @NonNull
    private final String queryBlocksJson;
    @NonNull
    private final String queryPermalink;
    private Ticket.@NonNull Status status;
    private String team;
    private String impact;
    @NonNull
    @Builder.Default
    private ImmutableList<@NonNull String> tags = ImmutableList.of();
    @NonNull
    @Builder.Default
    private ImmutableList<@NonNull StatusLog> logs = ImmutableList.of();
    @Builder.Default
    private boolean escalated = false;
    @Builder.Default
    private ImmutableList<@NonNull EscalationInfo> escalations = ImmutableList.of();

    public static TicketBuilder fromResponse(SupportBotClient.TicketResponse ticketResponse) {
        return Ticket.builder()
            .id(ticketResponse.id())
            .queryTs(ticketResponse.query().ts())
            .formMessageTs(ticketResponse.formMessage().ts())
            .channelId(ticketResponse.channelId())
            .status(Ticket.Status.fromCode(ticketResponse.status()))
            .team(
                ticketResponse.team() != null
                    ? ticketResponse.team().name()
                    : null
            )
            .tags(ticketResponse.tags())
            .logs(ticketResponse.logs());
    }

    public FullSummaryButtonClick fullSummaryButtonClick(String triggerId) {
        return FullSummaryButtonClick.builder()
            .triggerId(triggerId)
            .actionId("ticket-summary-view")
            .ticket(this)
            .slackWiremock(slackWiremock)
            .build();
    }

    public StubWithResult<FullSummaryForm> expectFullSummaryFormOpened(String triggerId) {
        var expectation = ViewsOpenExpectation.<FullSummaryForm>builder()
            .viewCallbackId("ticket-summary")
            .viewType("modal")
            .triggerId(triggerId)
            .receiver(new FullSummaryForm.Receiver(this))
            .build();

        // Stub permalink for the ticket form message (used for escalation thread links in summary)
        if (!escalations.isEmpty()) {
            slackWiremock.stubGetPermalink(channelId, formMessageTs);
        }

        return slackWiremock.stubViewsOpen(expectation);
    }

    public FullSummaryFormSubmission fullSummaryFormSubmit(String triggerId, FullSummaryFormSubmission.Values values) {
        return FullSummaryFormSubmission.builder()
            .triggerId(triggerId)
            .callbackId("ticket-summary")
            .ticketId(id)
            .values(values)
            .build();
    }

    public TicketUpdater applyChangesLocally() {
        return new TicketUpdater();
    }

    public CloseFlowStubs stubCloseFlow(MessageTs threadTs) {
        StubWithResult<TicketMessage> updated = slackWiremock.stubMessageUpdated(
            MessageUpdatedExpectation.<TicketMessage>builder()
                .channelId(channelId)
                .ts(formMessageTs)
                .threadTs(threadTs)
                .receiver(new TicketMessage.Receiver())
                .build()
        );
        Stub check = slackWiremock.stubReactionAdd(
            ReactionAddedExpectation.builder()
                .reaction("white_check_mark")
                .channelId(channelId)
                .ts(queryTs)
                .build()
        );
        return new CloseFlowStubs(updated, check);
    }

    public ReopenFlowStubs stubReopenFlow(MessageTs threadTs) {
        StubWithResult<TicketMessage> updated = slackWiremock.stubMessageUpdated(
            MessageUpdatedExpectation.<TicketMessage>builder()
                .channelId(channelId)
                .ts(formMessageTs)
                .threadTs(threadTs)
                .receiver(new TicketMessage.Receiver())
                .build()
        );
        Stub uncheck = slackWiremock.stubReactionRemove(
            ReactionAddedExpectation.builder()
                .reaction("white_check_mark")
                .channelId(channelId)
                .ts(queryTs)
                .build()
        );
        return new ReopenFlowStubs(updated, uncheck);
    }

    public void openSummaryAndSubmit(SlackTestKit asSupportSlack, String triggerId, FullSummaryFormSubmission.Values values) {
        StubWithResult<FullSummaryForm> opened = expectFullSummaryFormOpened(triggerId);
        asSupportSlack.clickMessageButton(fullSummaryButtonClick(triggerId));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> opened.assertIsCalled("full summary form opened"));
        asSupportSlack.submitView(fullSummaryFormSubmit(triggerId, values));
    }


    public EscalateFlowStubs stubEscalateFlow(String expectedSlackGroupId, MessageTs newEscalationMessageTs) {
        StubWithResult<EscalationMessage> escalationMessage = expectEscalationMessagePosted(expectedSlackGroupId, newEscalationMessageTs);
        Stub rocketReactionAdded = slackWiremock.stubReactionAdd(
            ReactionAddedExpectation.builder()
                .reaction("rocket")
                .channelId(channelId)
                .ts(queryTs)
                .build()
        );
        return new EscalateFlowStubs(escalationMessage, rocketReactionAdded);
    }

    public void openEscalationAndSubmit(SlackTestKit asSupportSlack, String triggerId, EscalationFormSubmission.Values values) {
        StubWithResult<EscalationForm> opened = expectEscalationFormOpened(triggerId);
        asSupportSlack.clickMessageButton(escalateButtonClick(triggerId));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> opened.assertIsCalled("escalation form opened"));
        asSupportSlack.submitView(escalationFormSubmit(triggerId, values));
    }

    public void assertMatches(SupportBotClient.TicketResponse response) {
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.query().ts()).isEqualTo(queryTs);
        assertThat(response.formMessage().ts()).isEqualTo(formMessageTs);
        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(status.code());
        if (team == null) {
            assertThat(response.team()).isNull();
        } else {
            assertThat(response.team().name()).isEqualTo(team);
        }
        assertThat(response.tags()).isEqualTo(tags());

        assertThat(response.escalated()).isEqualTo(escalated);
        assertThat(response.escalations()).hasSize(escalations.size());
        for (int i = 0; i < escalations.size(); i++) {
            var expectedEscalation = escalations.get(i);
            var actualEscalation = response.escalations().get(i);
            assertThat(actualEscalation.team().name()).isEqualTo(expectedEscalation.team());
            assertThat(actualEscalation.tags()).isEqualTo(expectedEscalation.tags());
            if (expectedEscalation.createdAt() != null) {
                assertThat(actualEscalation.openedAt()).isCloseTo(expectedEscalation.createdAt(), within(2, ChronoUnit.SECONDS));
            }
            if (expectedEscalation.resolvedAt() != null) {
                assertThat(actualEscalation.resolvedAt()).isCloseTo(expectedEscalation.resolvedAt(), within(2, ChronoUnit.SECONDS));
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

    @Override
    public long ticketId() {
        return id;
    }

    public EscalationButtonClick escalateButtonClick(String triggerId) {
        return EscalationButtonClick.builder()
            .triggerId(triggerId)
            .ticket(this)
            .build();
    }

    public StubWithResult<EscalationForm> expectEscalationFormOpened(String triggerId) {
        var expectation = ViewsOpenExpectation.<EscalationForm>builder()
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

    public StubWithResult<EscalationMessage> expectEscalationMessagePosted(String expectedSlackGroupId, MessageTs ts) {
        return slackWiremock.stubMessagePosted(ThreadMessagePostedExpectation.<EscalationMessage>builder()
            .receiver(new EscalationMessage.Receiver(expectedSlackGroupId))
            .from(UserRole.supportBot)
            .newMessageTs(ts)
            .channelId(channelId)
            .threadTs(queryTs)
            .build());
    }

    public void escalateViaTestApi(MessageTs ts, String team, ImmutableList<@NonNull String> tags) {
        supportBotClient.test().escalateTicket(SupportBotClient.EscalationToCreate.builder()
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

        public static Status fromLabel(String label) {
            for (Status s : values()) {
                if (s.label.equals(label)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown ticket status label: " + label);
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

    record StatusLog(
        String event,
        Instant date
    ) {
    }

    public record EscalationInfo(
        String team,
        ImmutableList<@NonNull String> tags,
        Instant createdAt,
        Instant resolvedAt
    ) {
    }

    public class TicketUpdater {
        public TicketUpdater applyFormValues(FullSummaryFormSubmission.Values values) {
            status = values.status();
            team = values.team();
            tags = values.tags();
            impact = values.impact();
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

    public record CloseFlowStubs(StubWithResult<TicketMessage> messageUpdated, Stub whiteCheckMarkAdded) {
        public void awaitAllCalled(Duration timeout, String reason) {
            await().atMost(timeout).untilAsserted(() -> {
                messageUpdated.assertIsCalled(reason + ": ticket form update");
                whiteCheckMarkAdded.assertIsCalled(reason + ": checkmark added");
            });
        }
    }

    public record ReopenFlowStubs(StubWithResult<TicketMessage> messageUpdated, Stub whiteCheckMarkRemoved) {
        public void awaitAllCalled(Duration timeout, String reason) {
            await().atMost(timeout).untilAsserted(() -> {
                messageUpdated.assertIsCalled(reason + ": ticket form update");
                whiteCheckMarkRemoved.assertIsCalled(reason + ": checkmark removed");
            });
        }
    }
    public record EscalateFlowStubs(StubWithResult<EscalationMessage> escalationMessage, Stub rocketReactionAdded) {
        public void awaitAllCalled(Duration timeout, String reason) {
            await().atMost(timeout).untilAsserted(() -> {
                escalationMessage.assertIsCalled(reason + ": escalation message posted");
                rocketReactionAdded.assertIsCalled(reason + ": rocket reaction added");
            });
        }
    }
}
