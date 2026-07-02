package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;

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
 * End-to-end lifecycle coverage for the GitHub code-owner merge gate (config {@code GH ·
 * requires-codeowners · SLA}, repo {@code test-org/pr-codeowners-repo}). Seeds records directly and
 * drives transitions via the GitHub v3/GraphQL stubs, mirroring the state-machine transition tests in
 * {@code PrLifecyclePollerFunctionalTests}.
 *
 * <p>These cover the matrix cells that had no functional coverage:
 *
 * <ul>
 *   <li><b>F2b</b> — merge-SLA breach in {@code AWAITING_MERGE} escalates the maintaining team
 *       ({@code MERGE_ESCALATED}), then closes only on the real merge. The core invariant is that a
 *       code-owner repo never closes on mergeability alone — only on the merge itself.
 *   <li><b>F3</b> — changes-requested → code-owner approved → {@code AWAITING_MERGE} → merged.
 *   <li><b>F4</b> — abandoned: a {@code closed}-but-unmerged PR terminates {@code AWAITING_MERGE}.
 * </ul>
 */
@ExtendWith(TestKitExtension.class)
public class PrCodeownerMergeGateFunctionalTests {
    private static final String CODEOWNERS_REPO = "test-org/pr-codeowners-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanUpPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    /** Returns a PR created-at timestamp 1 hour ago — safely within the 24h SLA window. */
    private static String recentCreatedAt() {
        return Instant.now().minus(Duration.ofHours(1)).toString();
    }

    // F2b — merge-SLA escalation then close-on-merge (the headline MERGE_ESCALATED gap).
    @Test
    public void whenAwaitingMergeSlaBreaches_escalatesMaintainingTeamThenClosesOnMerge() {
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

        // Seed straight into AWAITING_MERGE with an already-breached merge deadline.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .githubRepo(CODEOWNERS_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .status("AWAITING_MERGE")
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: code owners approved (GraphQL reviewDecision=APPROVED) and the PR is mergeable, but the
        // merge clock has already breached. The maintaining team is chased (AWAITING_MERGE → MERGE_ESCALATED)
        // — mirroring the review-phase escalation, this posts an escalation message and adds a rocket
        // reaction on the ticket query. Crucially the record must NOT close on mergeability alone.
        var prOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner PR open + mergeable, merge SLA breached",
                        CODEOWNERS_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        "[]");
        var graphQlStub = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision("Codeowner reviewDecision APPROVED", "APPROVED", List.of());
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

        prOpenStub.assertIsCalled();
        graphQlStub.assertIsCalled();
        escalationMessageStub.assertIsCalled();
        rocketReactionStub.assertIsCalled();
        var escalated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(escalated.status()).isEqualTo("MERGE_ESCALATED");
        assertThat(escalated.escalationId()).isNotNull();
        assertThat(escalated.closedAt()).isNull();

        // Poll 2: the maintaining team merges (provider reports the PR terminal via merged_at → PrState.MERGED).
        // Only now does the record close — the merge gate never closed it on mergeability alone. The escalation
        // id is preserved. The distinct "PR merged" FSM row fires NOTIFY_MERGED, posting the "merged" close text.
        var prMergedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequestMerged("Codeowner PR merged", CODEOWNERS_REPO, 1, recentCreatedAt());
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("merge close notification", channelId, "merged");

        supportBotClient.test().triggerPrTrackingPoll();

        prMergedStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.escalationId()).isNotNull();
    }

    // F3 — changes-requested → code-owner approved → AWAITING_MERGE → merged.
    @Test
    public void whenCodeownerChangesRequestedThenApproved_reachesAwaitingMergeThenClosesOnMerge() {
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

        // Seed OPEN with a live deadline. A code-owner repo holds the review clock in OPEN (the
        // OPEN → ESCALATED row is guarded by !requiresCodeowners), so a live deadline can't review-escalate.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .githubRepo(CODEOWNERS_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: a code owner requests changes → CHANGES_REQUESTED (review clock paused).
        String crReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prWithCrStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner PR changes requested",
                        CODEOWNERS_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        false,
                        crReviewJson);
        var crGraphQlStub = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision(
                        "Codeowner reviewDecision CHANGES_REQUESTED", "CHANGES_REQUESTED", List.of("owner1"));
        var crMessageStub = testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prWithCrStub.assertIsCalled();
        crGraphQlStub.assertIsCalled();
        crMessageStub.assertIsCalled();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("CHANGES_REQUESTED");

        // Poll 2: the code owners approve and the PR is mergeable → AWAITING_MERGE (not CLOSED — the
        // merge gate hands off to the maintaining team and resumes the paused clock rather than closing).
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T11:00:00Z","body":""}]
                """;
        var prApprovedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner PR approved + mergeable",
                        CODEOWNERS_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        approvedReviewJson);
        var approvedGraphQlStub = testKit.slack()
                .wiremock()
                .stubGitHubGraphQlReviewDecision("Codeowner reviewDecision APPROVED", "APPROVED", List.of());
        var awaitingMergeMsgStub =
                testKit.slack().wiremock().stubChatPostMessage("awaiting-merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prApprovedStub.assertIsCalled();
        approvedGraphQlStub.assertIsCalled();
        awaitingMergeMsgStub.assertIsCalled();
        var awaiting = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(awaiting.status()).isEqualTo("AWAITING_MERGE");
        assertThat(awaiting.closedAt()).isNull();

        // Poll 3: the maintaining team merges (merged_at → PrState.MERGED) → CLOSED via the "PR merged" row,
        // which fires NOTIFY_MERGED and posts the distinct "merged" close text.
        var prMergedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequestMerged("Codeowner PR merged", CODEOWNERS_REPO, 1, recentCreatedAt());
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("merge close notification", channelId, "merged");

        supportBotClient.test().triggerPrTrackingPoll();

        prMergedStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var closed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
    }

    // F4 — abandoned: a closed-but-unmerged PR terminates AWAITING_MERGE.
    @Test
    public void whenAwaitingMergePrClosedUnmerged_closesRecordWithoutMerge() {
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

        // Seed AWAITING_MERGE with a live deadline so a merge-SLA breach can't fire — the only exit under
        // test is the PR being closed unmerged.
        var record = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.id())
                        .githubRepo(CODEOWNERS_REPO)
                        .prNumber(1)
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .status("AWAITING_MERGE")
                        .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll: the PR is closed without being merged (state=closed, no merged_at) → CLOSED via the
        // "PR closed" row, which fires NOTIFY_CLOSED and posts the distinct "closed" close text (never "merged").
        // Closed PRs skip the code-owner gate, so no GraphQL stub is needed.
        var prClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "Codeowner PR closed unmerged", CODEOWNERS_REPO, 1, "closed", recentCreatedAt(), false, "[]");
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
