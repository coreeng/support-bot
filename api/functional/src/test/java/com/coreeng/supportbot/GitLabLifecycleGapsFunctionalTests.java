package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.ReactionAddedExpectation;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Lifecycle poller coverage for a GitLab, non-code-owner, SLA-configured repo (GL·noCO·SLA), filling
 * two gaps left by {@code PrLifecyclePollerFunctionalTests.GitLab}:
 *
 * <ul>
 *   <li><b>F2a</b> — review-SLA escalation then recover-and-close: the existing GitLab escalation test
 *       stops at {@code ESCALATED}; this drives the recovery leg
 *       ({@code ESCALATED → approved + mergeable → CLOSED}, {@code escalationId} preserved).
 *   <li><b>F4</b> — abandoned: an MR closed unmerged drives the record to {@code CLOSED} (there is no
 *       GitLab lifecycle-close test today; the only merged-MR test is a detection-time skip).
 * </ul>
 *
 * <p>The repo is non-code-owner, so no {@code approval_state} stub is involved and the FSM's
 * {@code !requiresCodeowners} guard holds on the {@code approved + mergeable → CLOSED} rows.
 */
@ExtendWith(TestKitExtension.class)
public class GitLabLifecycleGapsFunctionalTests {

    private static final String MR_REPO = "gitlab-org/gitlab-pr-test-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanupPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    /** Returns an MR created-at timestamp 1 hour ago, as an ISO string, per the GitLab templates. */
    private static String recentCreatedAt() {
        return Instant.now().minus(Duration.ofHours(1)).toString();
    }

    @Test
    public void whenGitLabMrSlaBreachesThenRecovers_escalatesThenClosesRecord() {
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

        // Seed OPEN with an already-breached deadline so the first poll review-escalates.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .provider("gitlab")
                        .githubRepo(MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: MR open + mergeable with no approvals and a breached SLA → ESCALATED.
        var mrOpenNoApprovalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 open no approvals",
                        MR_REPO,
                        42,
                        "opened",
                        "mergeable",
                        recentCreatedAt(),
                        recentCreatedAt());
        var noApprovalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals("MR !42 no approvals", MR_REPO, 42, List.of());
        var escalationMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("escalation notification", channelId);
        var rocketReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("rocket reaction on ticket query")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrOpenNoApprovalsStub.assertIsCalled();
            noApprovalsStub.assertIsCalled();
            escalationMessageStub.assertIsCalled();
            rocketReactionStub.assertIsCalled();
        });
        var escalated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(escalated.status()).isEqualTo("ESCALATED");
        assertThat(escalated.escalationId()).isNotNull();

        // Poll 2: the reviewer approves and the MR stays mergeable → ESCALATED → CLOSED
        // ("approved + mergeable", !requiresCodeowners holds — this repo is non-CO), escalationId preserved.
        // Group members served by the permanent catch-all stub (empty list) — TeamReviewFilter then falls
        // back to "accept all reviews" so the reviewer approval still counts.
        var mrApprovedMergeableStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 approved mergeable",
                        MR_REPO,
                        42,
                        "opened",
                        "mergeable",
                        recentCreatedAt(),
                        recentCreatedAt());
        var approvalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals("MR !42 approvals", MR_REPO, 42, List.of("reviewer"));
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved + mergeable notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrApprovedMergeableStub.assertIsCalled();
            approvalsStub.assertIsCalled();
            closeMessageStub.assertIsCalled();
        });
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.escalationId()).isNotNull();
    }

    @Test
    public void whenGitLabMrClosedUnmerged_closesRecordAndPostsSlackMessage() {
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

        // Seed OPEN with a live deadline; the MR is then abandoned (closed unmerged).
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .provider("gitlab")
                        .githubRepo(MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // MR closed (not merged) → terminal CLOSED. Approvals/approval_state are not fetched once the
        // MR is no longer open, so only the MR-get + close-notice stubs are needed.
        var mrClosedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 closed", MR_REPO, 42, "closed", "not_open", recentCreatedAt(), recentCreatedAt());
        var closeMessageStub = testKit.slack().wiremock().stubChatPostMessage("MR closed notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrClosedStub.assertIsCalled();
            closeMessageStub.assertIsCalled();
        });
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }
}
