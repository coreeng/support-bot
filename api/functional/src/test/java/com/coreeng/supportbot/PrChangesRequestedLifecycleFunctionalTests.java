package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * End-to-end changes-requested lifecycle for a GitHub non-code-owner repo with an SLA
 * (GH · noCO · SLA, flow F3). Drives one record through
 * {@code OPEN → CHANGES_REQUESTED → APPROVED → CLOSED} in a single run, chaining the three legs that
 * exist piecemeal across the poller tests. Being a non-code-owner repo, no GraphQL review-decision
 * stub is involved.
 */
@ExtendWith(TestKitExtension.class)
public class PrChangesRequestedLifecycleFunctionalTests {
    private static final String PR_REPO = "test-org/pr-test-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanupPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    /** Returns a PR created-at timestamp 1 hour ago — safely within the 24h SLA window. */
    private static String recentCreatedAt() {
        return Instant.now().minus(Duration.ofHours(1)).toString();
    }

    @Test
    public void whenChangesRequestedThenApprovedThenMergeable_progressesFromOpenToClosed() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient
                .test()
                .createTicket(SupportBotClient.TicketToCreateRequest.builder()
                        .channelId(channelId)
                        .queryTs(queryTs)
                        .createdMessageTs(ticketTs)
                        .build());

        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .githubRepo(PR_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: reviewer requests changes → CHANGES_REQUESTED + Slack notification, SLA paused.
        String crReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prWithCrStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 changes requested", PR_REPO, 1, "open", recentCreatedAt(), false, crReviewJson);
        var crMessageStub = testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prWithCrStub.assertIsCalled();
        crMessageStub.assertIsCalled();
        var changesRequested = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(changesRequested.status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(changesRequested.slaDeadline()).isNull();
        assertThat(changesRequested.slaRemaining()).isNotNull().isPositive();

        // Poll 2: reviewer approves, merge conflicts still present → APPROVED (SLA remains paused, no Slack).
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T11:00:00Z","body":""}]
                """;
        var prApprovedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved not mergeable",
                        PR_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        false,
                        approvedReviewJson);
        var unexpectedMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prApprovedStub.assertIsCalled();
        unexpectedMessageStub.assertIsNotCalled();
        unexpectedMessageStub.cleanUp();
        var approved = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.slaDeadline()).isNull();
        assertThat(approved.slaRemaining()).isNotNull().isPositive();

        // Poll 3: merge conflicts resolved → CLOSED + Slack close notification.
        var prMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved mergeable", PR_REPO, 1, "open", recentCreatedAt(), true, "[]");
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved and ready to merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prMergeableStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }
}
