package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;

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
 * End-to-end lifecycle coverage for the two GitHub <b>no-SLA</b> configs — non-code-owner ({@code GH ·
 * noCO · noSLA}, repo {@code test-org/pr-nosla-repo}) and code-owner ({@code GH · CO · noSLA}, repo
 * {@code test-org/pr-codeowners-nosla-repo}). No-SLA repos have {@code sla} unset and are tracked by
 * changed-file {@code paths} matching; their records carry <b>no deadline</b> ({@code slaDeadline =
 * null}), so they have no live review clock and their merge clock is a no-op.
 *
 * <p>Seeds records directly (with a null {@code slaDeadline}) and drives transitions via the GitHub
 * v3/GraphQL stubs, mirroring the state-machine transition tests in
 * {@code PrLifecyclePollerFunctionalTests} and {@code PrCodeownerMergeGateFunctionalTests}. Every
 * transition asserted here corresponds to a row in {@code PrLifecycle#TRANSITIONS}.
 *
 * <p>The load-bearing no-SLA invariants proved below:
 *
 * <ul>
 *   <li><b>Never review-escalates:</b> with no deadline, {@code slaBreached} can never be true, so the
 *       {@code OPEN → ESCALATED} row never fires.
 *   <li><b>Never merge-escalates:</b> entering {@code AWAITING_MERGE} runs {@code SlaOp.Start} as a
 *       no-op (no configured SLA and nothing paused → no deadline stamped), so the {@code
 *       AWAITING_MERGE → MERGE_ESCALATED} "merge SLA breached" row can never fire — even when the
 *       record is polled repeatedly while still open + code-owner-approved + mergeable.
 *   <li><b>No-SLA changes-requested:</b> a changes-requested review on a no-SLA record with no stored
 *       remaining takes the {@code "changes requested, no-SLA record"} row ({@code slaRemaining} stays
 *       null), not the paused-deadline row.
 * </ul>
 *
 * <p>The code-owner "merged" polls stub a real merge with {@code stubGitHubGetPullRequestMerged}
 * ({@code state:"closed"} <b>with</b> a non-null {@code merged_at}), which hub4j maps to {@code
 * PrState.MERGED} → the {@code "PR merged"} FSM row → {@code NOTIFY_MERGED}; those assert the distinct
 * {@code "merged"} close text, while the closed-unmerged polls (no {@code merged_at}) assert {@code
 * "closed"}. The non-code-owner tests have no provider-merge terminal (they close on approval or on a
 * closed-unmerged PR), so they keep the plain {@code stubGitHubGetPullRequest} and assert on {@code
 * status}/{@code closedAt}.
 */
@ExtendWith(TestKitExtension.class)
public class PrNoSlaLifecycleFunctionalTests {
    private static final String NOSLA_REPO = "test-org/pr-nosla-repo";
    private static final String CODEOWNERS_NOSLA_REPO = "test-org/pr-codeowners-nosla-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanUpPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    /** Returns a PR created-at timestamp 1 hour ago. */
    private static String recentCreatedAt() {
        return Instant.now().minus(Duration.ofHours(1)).toString();
    }

    // -------------------------------------------------------------------------
    // Non-code-owner no-SLA repo (test-org/pr-nosla-repo)
    // -------------------------------------------------------------------------

    // F1 — OPEN (no deadline) → approved + mergeable → CLOSED directly (no merge gate, non-CO repo).
    @Test
    public void nonCodeownerNoSla_approvedAndMergeable_closesRecordFromOpen() {
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

        // Seed OPEN with a null slaDeadline — a no-SLA record carries no review clock.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .githubRepo(NOSLA_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(null)
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).slaDeadline())
                .isNull();

        // Poll: approved + mergeable on a non-code-owner repo → CLOSED via the OPEN "approved + mergeable" row.
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "No-SLA PR approved + mergeable",
                        NOSLA_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        approvedReviewJson);
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved and ready to merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var result = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.closedAt()).isNotNull();
    }

    // F3 — OPEN → changes requested → CHANGES_REQUESTED (no-SLA row) → approved not mergeable → APPROVED
    // → mergeable → CLOSED. Asserts the no-SLA changes-requested row keeps slaRemaining null.
    @Test
    public void nonCodeownerNoSla_changesRequestedThenApproved_resolvesToClosed() {
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
                        .githubRepo(NOSLA_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(null)
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: changes requested → CHANGES_REQUESTED via the "changes requested, no-SLA record" row
        // (no live deadline, no stored remaining). No SLA is paused, so slaRemaining stays null.
        String crReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prWithCrStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "No-SLA PR changes requested", NOSLA_REPO, 1, "open", recentCreatedAt(), false, crReviewJson);
        var crMessageStub = testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prWithCrStub.assertIsCalled();
        crMessageStub.assertIsCalled();
        var changesRequested = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(changesRequested.status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(changesRequested.slaDeadline()).isNull();
        assertThat(changesRequested.slaRemaining()).isNull();

        // Poll 2: reviewer approves but the PR is not mergeable → APPROVED (no notification).
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T11:00:00Z","body":""}]
                """;
        var prApprovedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "No-SLA PR approved not mergeable",
                        NOSLA_REPO,
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
        assertThat(approved.slaRemaining()).isNull();

        // Poll 3: merge conflicts resolved → CLOSED via the APPROVED "mergeable" row.
        var prMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "No-SLA PR approved mergeable", NOSLA_REPO, 1, "open", recentCreatedAt(), true, "[]");
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved and ready to merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prMergeableStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    // F4 — OPEN → PR closed unmerged → CLOSED via the "PR closed" row.
    @Test
    public void nonCodeownerNoSla_prClosedUnmerged_closesRecord() {
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
                        .githubRepo(NOSLA_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(null)
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll: the PR is closed (state=closed, no merged_at) → CLOSED. The closed rows fire regardless of
        // deadline, so a no-SLA record terminates the same way as an SLA one.
        var prClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("No-SLA PR closed", NOSLA_REPO, 1, "closed", recentCreatedAt(), false, "[]");
        var closeMessageStub = testKit.slack().wiremock().stubChatPostMessage("closed notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prClosedStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Code-owner no-SLA repo (test-org/pr-codeowners-nosla-repo)
    // -------------------------------------------------------------------------

    // F1 — OPEN → code-owner approved + mergeable → AWAITING_MERGE (no deadline). An extra poll while still
    // open + approved + mergeable proves the record STAYS AWAITING_MERGE and never merge-escalates (the
    // no-op merge clock). Then merged → CLOSED.
    @Test
    public void codeownerNoSla_awaitingMerge_neverMergeEscalatesThenClosesOnMerge() {
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
                        .githubRepo(CODEOWNERS_NOSLA_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(null)
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: code owners approved (GraphQL reviewDecision=APPROVED) and the PR is mergeable → hand off
        // to AWAITING_MERGE. SlaOp.Start is a no-op here (no configured SLA, nothing paused), so the record
        // enters AWAITING_MERGE with no deadline.
        var prOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner no-SLA PR open + mergeable",
                        CODEOWNERS_NOSLA_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        "[]");
        var graphQlStub = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision("Codeowner no-SLA reviewDecision APPROVED", "APPROVED", List.of());
        var awaitingMergeMsgStub =
                testKit.slack().wiremock().stubChatPostMessage("awaiting-merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prOpenStub.assertIsCalled();
        graphQlStub.assertIsCalled();
        awaitingMergeMsgStub.assertIsCalled();
        var awaiting = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(awaiting.status()).isEqualTo("AWAITING_MERGE");
        assertThat(awaiting.slaDeadline()).isNull();
        assertThat(awaiting.closedAt()).isNull();

        // Poll 2: still open, code-owner approved and mergeable, but the merge clock is a no-op — with no
        // deadline the "merge SLA breached" row can never fire, so the record STAYS AWAITING_MERGE (never
        // MERGE_ESCALATED) and posts nothing.
        var prStillOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner no-SLA PR still open + mergeable",
                        CODEOWNERS_NOSLA_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        "[]");
        var graphQlStub2 = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision(
                        "Codeowner no-SLA reviewDecision APPROVED again", "APPROVED", List.of());
        var unexpectedMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected merge-escalation notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prStillOpenStub.assertIsCalled();
        graphQlStub2.assertIsCalled();
        unexpectedMessageStub.assertIsNotCalled();
        unexpectedMessageStub.cleanUp();
        var stillAwaiting = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(stillAwaiting.status()).isEqualTo("AWAITING_MERGE");
        assertThat(stillAwaiting.escalationId()).isNull();
        assertThat(stillAwaiting.closedAt()).isNull();

        // Poll 3: the maintaining team merges (merged_at → PrState.MERGED) → CLOSED via the "PR merged" row,
        // which fires NOTIFY_MERGED and posts the distinct "merged" close text. Closed PRs skip the code-owner
        // gate, so no GraphQL stub is needed.
        var prMergedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequestMerged(
                        "Codeowner no-SLA PR merged", CODEOWNERS_NOSLA_REPO, 1, recentCreatedAt());
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("merge close notification", channelId, "merged");

        supportBotClient.test().triggerPrTrackingPoll();

        prMergedStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    // F3 — OPEN → changes requested (GraphQL reviewDecision=CHANGES_REQUESTED) → CHANGES_REQUESTED → code-owner approved +
    // mergeable (GraphQL APPROVED) → AWAITING_MERGE → merged → CLOSED.
    @Test
    public void codeownerNoSla_changesRequestedThenApproved_reachesAwaitingMergeThenCloses() {
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
                        .githubRepo(CODEOWNERS_NOSLA_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(null)
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: a code owner requests changes (GraphQL reviewDecision=CHANGES_REQUESTED — under the
        // code-owner merge gate the aggregate provider decision, not individual REST reviews, drives the
        // review-phase verdict) → CHANGES_REQUESTED via the "changes requested, no-SLA record" row. No SLA
        // is paused.
        String crReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prWithCrStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner no-SLA PR changes requested",
                        CODEOWNERS_NOSLA_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        false,
                        crReviewJson);
        var crGraphQlStub = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision(
                        "Codeowner no-SLA reviewDecision CHANGES_REQUESTED", "CHANGES_REQUESTED", List.of("owner1"));
        var crMessageStub = testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prWithCrStub.assertIsCalled();
        crGraphQlStub.assertIsCalled();
        crMessageStub.assertIsCalled();
        var changesRequested = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(changesRequested.status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(changesRequested.slaRemaining()).isNull();

        // Poll 2: the code owners approve and the PR is mergeable → AWAITING_MERGE (not CLOSED — the merge
        // gate hands off to the maintaining team). Start is a no-op, so it enters with no deadline.
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T11:00:00Z","body":""}]
                """;
        var prApprovedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner no-SLA PR approved + mergeable",
                        CODEOWNERS_NOSLA_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        approvedReviewJson);
        var approvedGraphQlStub = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision("Codeowner no-SLA reviewDecision APPROVED", "APPROVED", List.of());
        var awaitingMergeMsgStub =
                testKit.slack().wiremock().stubChatPostMessage("awaiting-merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prApprovedStub.assertIsCalled();
        approvedGraphQlStub.assertIsCalled();
        awaitingMergeMsgStub.assertIsCalled();
        var awaiting = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(awaiting.status()).isEqualTo("AWAITING_MERGE");
        assertThat(awaiting.slaDeadline()).isNull();
        assertThat(awaiting.closedAt()).isNull();

        // Poll 3: the maintaining team merges (merged_at → PrState.MERGED) → CLOSED via the "PR merged" row,
        // which fires NOTIFY_MERGED and posts the distinct "merged" close text.
        var prMergedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequestMerged(
                        "Codeowner no-SLA PR merged", CODEOWNERS_NOSLA_REPO, 1, recentCreatedAt());
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("merge close notification", channelId, "merged");

        supportBotClient.test().triggerPrTrackingPoll();

        prMergedStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    // F4 — seeded AWAITING_MERGE (no deadline) → PR closed unmerged → CLOSED via the "PR closed" row.
    @Test
    public void codeownerNoSla_awaitingMergePrClosedUnmerged_closesRecord() {
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

        // Seed straight into AWAITING_MERGE with a null slaDeadline (a no-SLA merge phase carries no clock).
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .githubRepo(CODEOWNERS_NOSLA_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .status("AWAITING_MERGE")
                        .slaDeadline(null)
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());
        var seeded = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(seeded.status()).isEqualTo("AWAITING_MERGE");
        assertThat(seeded.slaDeadline()).isNull();

        // Poll: the PR is closed without being merged (state=closed, no merged_at) → CLOSED via the "PR closed"
        // row, which fires NOTIFY_CLOSED and posts the distinct "closed" close text (never "merged"). Closed PRs
        // skip the code-owner gate.
        var prClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner no-SLA PR closed unmerged",
                        CODEOWNERS_NOSLA_REPO,
                        1,
                        "closed",
                        recentCreatedAt(),
                        false,
                        "[]");
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("closed notification", channelId, "closed");

        supportBotClient.test().triggerPrTrackingPoll();

        prClosedStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }
}
