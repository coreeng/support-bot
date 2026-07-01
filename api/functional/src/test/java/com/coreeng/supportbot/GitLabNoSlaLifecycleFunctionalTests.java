package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coreeng.supportbot.testkit.MessageTs;
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
 * End-to-end lifecycle poller tests for GitLab <b>no-SLA</b> repos — both non-code-owner
 * (config {@code GitLab · no requires-codeowners · no-SLA}) and code-owner
 * (config {@code GitLab · requires-codeowners · no-SLA}). No-SLA repos leave {@code sla} unset and are
 * tracked by changed-file {@code paths} matching; a seeded record therefore has {@code slaDeadline=null}
 * and never has a live deadline, so it can never review-escalate nor merge-escalate.
 *
 * <p>These are seed-and-poll tests: they seed the record directly and drive the GitLab v4 API stubs,
 * bypassing detection. The no-SLA changed-file path filter (commit {@code 4f238682}) lives in
 * {@code PrDetectionService} at detection time only; a poll never lists MR changes (the lifecycle
 * poller resolves no merge SLA for a repo with no {@code sla} block, so {@code SlaLookup} — the only
 * caller that would fetch changes — is never invoked), so no {@code stubGitLabListChanges} is needed.
 *
 * <p>The code-owner gate is read from {@code /merge_requests/:iid/approval_state} and is only fetched
 * while the MR is OPEN and only for requires-codeowners repos (see
 * {@code GitLabPrSourceClient.fetchPullRequest}) — so the open polls of the code-owner repo stub
 * {@code approval_state} while the merged/closed polls do not.
 */
@ExtendWith(TestKitExtension.class)
public class GitLabNoSlaLifecycleFunctionalTests {

    private static final String NOSLA_MR_REPO = "gitlab-org/gitlab-pr-nosla-repo";
    private static final String CODEOWNERS_NOSLA_MR_REPO = "gitlab-org/gitlab-pr-codeowners-nosla-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanupPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    /**
     * F1 (non-CO no-SLA) — happy path: OPEN with no deadline → approving reviewer + mergeable → CLOSED.
     * A non-codeowner repo closes on mergeability alone; the no-SLA record carries {@code slaDeadline=null}
     * throughout, so there is never a deadline to breach.
     */
    @Test
    public void whenNonCodeownerNoSlaApprovedAndMergeable_closesRecord() {
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

        // No slaDeadline set → seeded with a null deadline (no-SLA record). Verify the null round-trips
        // through the nullable DB column before polling.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .provider("gitlab")
                        .githubRepo(NOSLA_MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());
        assertThat(record.slaDeadline()).isNull();

        String createdAt = Instant.now().minus(Duration.ofHours(1)).toString();

        // Approved + mergeable on an open MR drives a non-codeowner record straight to CLOSED. As a
        // non-codeowner repo it fetches approvals (MR open) but never approval_state.
        var mrStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 approved mergeable", NOSLA_MR_REPO, 42, "opened", "mergeable", createdAt, createdAt);
        var approvalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals("MR !42 approvals", NOSLA_MR_REPO, 42, List.of("reviewer"));
        // Group members served by the permanent catch-all stub (empty list) — TeamReviewFilter then
        // falls back to "accept all reviews" so the reviewer approval still counts.
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved + mergeable notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrStub.assertIsCalled();
            approvalsStub.assertIsCalled();
            closeMessageStub.assertIsCalled();
        });
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    /**
     * F4 (non-CO no-SLA) — abandoned: an OPEN no-SLA record whose MR is closed unmerged terminates as
     * CLOSED. {@code GitLabPrSourceClient.mapState} maps {@code "closed"} to a non-merged terminal (the
     * "PR closed" CLOSED edge), distinct from {@code "merged"}. The MR is terminal so approvals are not
     * fetched.
     */
    @Test
    public void whenNonCodeownerNoSlaMrClosedUnmerged_closesRecord() {
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
                        .githubRepo(NOSLA_MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());
        assertThat(record.slaDeadline()).isNull();

        String createdAt = Instant.now().minus(Duration.ofHours(1)).toString();

        // The MR is closed without being merged. mapState("closed") → non-merged terminal → the "PR
        // closed" CLOSED edge fires (distinct from the "PR merged" edge for state "merged").
        var mrClosedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 closed unmerged", NOSLA_MR_REPO, 42, "closed", "not_open", createdAt, createdAt);
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

    /**
     * F1 (CO no-SLA) — happy path: OPEN (no deadline) → code owners approved + mergeable →
     * AWAITING_MERGE → merged → CLOSED. Because the repo has no SLA, the AWAITING_MERGE entry starts no
     * merge clock ({@code slaDeadline} stays null), so a re-poll while still awaiting merge must
     * <b>stay</b> AWAITING_MERGE and never reach MERGE_ESCALATED — the load-bearing no-SLA invariant
     * (the {@code SlaOp.Start} no-op path). The record still closes only on the real merge, never on
     * mergeability.
     */
    @Test
    public void whenCodeownerNoSlaApprovedAndMergeable_entersAwaitingMergeNeverEscalatesThenClosesOnMerge() {
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
                        .githubRepo(CODEOWNERS_NOSLA_MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());
        assertThat(record.slaDeadline()).isNull();

        String createdAt = Instant.now().minus(Duration.ofHours(1)).toString();

        // Poll 1: MR open + mergeable, an approving reviewer, and the code-owner gate satisfied
        // (approval_state code_owner rule approved). The record hands off to AWAITING_MERGE — but with
        // no SLA the merge clock is a no-op, so slaDeadline stays null and it does NOT close on
        // mergeability.
        var mrOpenStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 open + mergeable",
                        CODEOWNERS_NOSLA_MR_REPO,
                        42,
                        "opened",
                        "mergeable",
                        createdAt,
                        createdAt);
        var approvalsStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals(
                        "MR !42 approvals", CODEOWNERS_NOSLA_MR_REPO, 42, List.of("reviewer"));
        var approvalStateStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovalState(
                        "MR !42 code-owner gate approved", CODEOWNERS_NOSLA_MR_REPO, 42, true, List.of());
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
        // No merge clock was started (no SLA) — the deadline remains null.
        assertThat(awaiting.slaDeadline()).isNull();

        // Poll 2: re-observe the still-open, still-approved MR while AWAITING_MERGE. With no deadline the
        // merge SLA can never breach, so the record STAYS AWAITING_MERGE and never reaches
        // MERGE_ESCALATED (proving the no-SLA merge clock is a no-op). No transition means no effect, so
        // no Slack message is posted.
        var mrStillOpenStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 still open + mergeable",
                        CODEOWNERS_NOSLA_MR_REPO,
                        42,
                        "opened",
                        "mergeable",
                        createdAt,
                        createdAt);
        var approvalsAgainStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovals(
                        "MR !42 approvals re-poll", CODEOWNERS_NOSLA_MR_REPO, 42, List.of("reviewer"));
        var approvalStateAgainStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequestApprovalState(
                        "MR !42 code-owner gate still approved", CODEOWNERS_NOSLA_MR_REPO, 42, true, List.of());

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            mrStillOpenStub.assertIsCalled();
            approvalsAgainStub.assertIsCalled();
            approvalStateAgainStub.assertIsCalled();
        });
        var stillAwaiting = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(stillAwaiting.status()).isEqualTo("AWAITING_MERGE");
        assertThat(stillAwaiting.closedAt()).isNull();
        assertThat(stillAwaiting.slaDeadline()).isNull();

        // Poll 3: the maintaining team merges (MR now terminal). Only now does the record close — the
        // merge gate never closed it on mergeability alone. Approvals/approval_state are not stubbed: the
        // client skips them once the MR is not OPEN.
        var mrMergedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 merged", CODEOWNERS_NOSLA_MR_REPO, 42, "merged", "not_open", createdAt, createdAt);
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
     * F4 (CO no-SLA) — abandoned: an AWAITING_MERGE no-SLA record whose MR is closed unmerged terminates
     * as CLOSED. Seeded directly in AWAITING_MERGE with a null deadline (no merge clock). The MR is
     * terminal so the code-owner gate is not fetched; {@code mapState("closed")} → non-merged terminal →
     * the "PR closed" CLOSED edge.
     */
    @Test
    public void whenCodeownerNoSlaAwaitingMergeMrClosedUnmerged_closesRecord() {
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

        // Seed straight into AWAITING_MERGE with no deadline (no-SLA merge phase). Verify both the status
        // and the null deadline round-trip.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .provider("gitlab")
                        .githubRepo(CODEOWNERS_NOSLA_MR_REPO)
                        .prNumber(42)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .status("AWAITING_MERGE")
                        .build());
        assertThat(record.status()).isEqualTo("AWAITING_MERGE");
        assertThat(record.slaDeadline()).isNull();

        String createdAt = Instant.now().minus(Duration.ofHours(1)).toString();

        // The MR is closed without being merged. The MR is terminal so the code-owner gate is not
        // fetched; mapState("closed") → non-merged terminal → the "PR closed" CLOSED edge.
        var mrClosedStub = testKit.slack()
                .wiremock()
                .stubGitLabGetMergeRequest(
                        "MR !42 closed unmerged",
                        CODEOWNERS_NOSLA_MR_REPO,
                        42,
                        "closed",
                        "not_open",
                        createdAt,
                        createdAt);
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
