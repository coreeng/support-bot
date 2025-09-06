package com.coreeng.supportbot.testkit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
    private String status;
    private String team;
    private String impact;
    @NonNull
    @Builder.Default
    private ImmutableList<@NonNull String> tags = ImmutableList.of();
    @NonNull
    @Builder.Default
    private ImmutableList<@NonNull StatusLog> logs = ImmutableList.of();

    @NonNull
    private final SlackWiremock slackWiremock;
    private final Config.@NonNull User user;
    @NonNull
    private final String teamId;
    @NonNull
    private final String queryBlocksJson;
    @NonNull
    private final String queryPermalink;

    public static TicketBuilder fromResponse(SupportBotClient.TicketResponse ticketResponse) {
        return Ticket.builder()
            .id(ticketResponse.id())
            .queryTs(ticketResponse.query().ts())
            .formMessageTs(ticketResponse.formMessage().ts())
            .channelId(ticketResponse.channelId())
            .status(ticketResponse.status())
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
            .receiver(new FullSummaryForm.Reciever(this))
            .build();
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


    public void assertMatches(SupportBotClient.TicketResponse response) {
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.query().ts()).isEqualTo(queryTs);
        assertThat(response.formMessage().ts()).isEqualTo(formMessageTs);
        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.team().name()).isEqualTo(team);
        assertThat(response.tags()).isEqualTo(tags());

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
    }

    record StatusLog(
        String event,
        Instant date
    ) {
    }
}
