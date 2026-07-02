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
 * End-to-end lifecycle poller tests for the GitLab code-owner merge gate
 * (config {@code GitLab · requires-codeowners · SLA}). Seed a record with {@code provider=gitlab}
 * against a requires-codeowners GitLab repo, then drive transitions via the GitLab v4 API stubs.
 *
 * <p>The code-owner gate is read from {@code /merge_requests/:iid/approval_state} and is only
 * fetched while the MR is OPEN and only for requires-codeowners repos
 * (see {@code GitLabPrSourceClient.fetchPullRequest}) — so the open polls stub {@code approval_state}
 * while the merged/closed polls do not.
 */
@ExtendWith(TestKitExtension.class)
public class GitLabCodeownerLifecycleFunctionalTests {

    private static final String MR_REPO = "gitlab-org/gitlab-pr-codeowners-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanupPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    /**
     * F1 — happy path: OPEN → AWAITING_MERGE (code owners approved + mergeable) → merged → CLOSED.
     * A code-owner repo must never close on mergeability alone; it hands off to AWAITING_MERGE and
     * closes only on the real merge.
     */
    @Test
    public void whenCodeownerGateApprovedAndMergeable_entersAwaitingMergeThenClosesOnMerge() {
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
                        .provider("gitlab")
                        .githubRepo(MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        String createdAt = Instant.now().minus(Duration.ofHours(1)).toString();

        // Poll 1: MR open + mergeable, an approving reviewer, and the code-owner gate satisfied
        // (approval_state code_owner rule approved). A non-codeowner repo would CLOSE here; this
        // repo must instead hand off to AWAITING_MERGE and must NOT close on mergeability.
        var mrOpenStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 open + mergeable", MR_REPO, 42, "opened", "mergeable", createdAt, createdAt);
        var approvalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals("MR !42 approvals", MR_REPO, 42, List.of("reviewer"));
        // Group members served by the permanent catch-all stub (empty list) — TeamReviewFilter then
        // falls back to "accept all reviews" so the reviewer approval still counts.
        var approvalStateStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovalState(
                        "MR !42 code-owner gate approved", MR_REPO, 42, true, List.of());
        var awaitingMergeMsgStub =
                testKit.slack().wiremock().stubChatPostMessage("awaiting-merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrOpenStub.assertIsCalled();
            approvalsStub.assertIsCalled();
            approvalStateStub.assertIsCalled();
            awaitingMergeMsgStub.assertIsCalled();
        });
        var awaiting = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(awaiting.status()).isEqualTo("AWAITING_MERGE");
        assertThat(awaiting.closedAt()).isNull();

        // Poll 2: the maintaining team merges (MR now terminal). Only now does the record close — the
        // merge gate never closed it on mergeability alone. Approvals/approval_state are not stubbed:
        // the client skips them once the MR is not OPEN.
        var mrMergedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest("MR !42 merged", MR_REPO, 42, "merged", "not_open", createdAt, createdAt);
        var closeMsgStub = testKit.slack().wiremock().stubChatPostMessage("merge close notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrMergedStub.assertIsCalled();
            closeMsgStub.assertIsCalled();
        });
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    /**
     * F2b — merge-SLA escalation then close-on-merge. Seeded directly in AWAITING_MERGE with an
     * already-breached merge deadline. Poll 1 re-observes the open MR (a CO repo, so it fetches
     * approvals + approval_state) and, finding the merge SLA breached, escalates the maintaining team
     * (custom/escalation-card {@code chat.postMessage} + rocket reaction on the ticket query ts).
     * Poll 2 merges → CLOSED with the escalation id preserved.
     */
    @Test
    public void whenAwaitingMergeSlaBreaches_escalatesThenClosesOnMerge() {
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
                        .provider("gitlab")
                        .githubRepo(MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(25)))
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .status("AWAITING_MERGE")
                        .build());

        String createdAt = Instant.now().minus(Duration.ofHours(25)).toString();

        // Poll 1: the poll re-observes the still-open MR. As a CO repo it fetches approvals +
        // approval_state (both approved); with no changes-requested verdict and the merge SLA
        // breached, AWAITING_MERGE escalates to MERGE_ESCALATED.
        var mrOpenStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 open + mergeable", MR_REPO, 42, "opened", "mergeable", createdAt, createdAt);
        var approvalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals("MR !42 approvals", MR_REPO, 42, List.of("reviewer"));
        var approvalStateStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovalState(
                        "MR !42 code-owner gate approved", MR_REPO, 42, true, List.of());
        // EscalateMerge (via doEscalate) posts the escalation card to the channel and marks the ticket
        // escalated with a rocket reaction on the query ts — mirrors the review-phase GitLab escalation.
        var escalationMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("merge escalation notification", channelId);
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
            mrOpenStub.assertIsCalled();
            approvalsStub.assertIsCalled();
            approvalStateStub.assertIsCalled();
            escalationMessageStub.assertIsCalled();
            rocketReactionStub.assertIsCalled();
        });
        var escalated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(escalated.status()).isEqualTo("MERGE_ESCALATED");
        assertThat(escalated.escalationId()).isNotNull();
        assertThat(escalated.closedAt()).isNull();

        // Poll 2: the maintaining team merges → CLOSED, escalation id preserved. MR is terminal so
        // approvals/approval_state are not fetched.
        var mrMergedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest("MR !42 merged", MR_REPO, 42, "merged", "not_open", createdAt, createdAt);
        var closeMsgStub = testKit.slack().wiremock().stubChatPostMessage("merge close notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrMergedStub.assertIsCalled();
            closeMsgStub.assertIsCalled();
        });
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.escalationId()).isNotNull();
    }

    /**
     * F4 — abandoned: an AWAITING_MERGE record whose MR is closed unmerged terminates as CLOSED.
     * {@code GitLabPrSourceClient.mapState} maps {@code "closed"} to a non-merged terminal (the
     * "PR closed" CLOSED edge), distinct from {@code "merged"}. The MR is terminal so the code-owner
     * gate is not fetched.
     */
    @Test
    public void whenAwaitingMergeMrClosedUnmerged_closesRecord() {
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
                        .provider("gitlab")
                        .githubRepo(MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .status("AWAITING_MERGE")
                        .build());

        String createdAt = Instant.now().minus(Duration.ofHours(1)).toString();

        // The MR is closed without being merged. mapState("closed") → non-merged terminal → the
        // "PR closed" CLOSED edge fires (distinct from the "PR merged" edge for state "merged").
        var mrClosedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 closed unmerged", MR_REPO, 42, "closed", "not_open", createdAt, createdAt);
        var closeMsgStub = testKit.slack().wiremock().stubChatPostMessage("close notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrClosedStub.assertIsCalled();
            closeMsgStub.assertIsCalled();
        });
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }
}
