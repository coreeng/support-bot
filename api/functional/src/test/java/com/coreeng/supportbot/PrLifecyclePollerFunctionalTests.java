package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coreeng.supportbot.testkit.EphemeralMessageExpectation;
import com.coreeng.supportbot.testkit.MessageToGet;
import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.MessageUpdatedExpectation;
import com.coreeng.supportbot.testkit.RatingRequestMessage;
import com.coreeng.supportbot.testkit.ReactionAddedExpectation;
import com.coreeng.supportbot.testkit.SlackMessage;
import com.coreeng.supportbot.testkit.SlackTestKit;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import com.coreeng.supportbot.testkit.TicketByIdQuery;
import com.coreeng.supportbot.testkit.TicketMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
public class PrLifecyclePollerFunctionalTests {
    private static final String PR_REPO = "test-org/pr-test-repo";

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

    @Test
    public void whenTrackedPrClosesAfterInitialTracking_pollerClosesTicketIfLastActivePr() {
        String queryText = "Please review my PR";
        SeededTicket ticket = createOpenedTicket(queryText);

        Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
        supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.ticketId())
                        .githubRepo(PR_REPO)
                        .prNumber(1)
                        .prCreatedAt(prCreatedAt)
                        .slaDeadline(prCreatedAt.plus(Duration.ofHours(24)))
                        .owningTeam("wow")
                        .build());

        var githubClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("Poll GitHub PR closed", PR_REPO, 1, "closed", prCreatedAt.toString());
        var closeNoticeStub = testKit.slack().wiremock().stubChatPostMessage("Poll close notice", ticket.channelId());
        var updateStub = testKit.slack()
                .wiremock()
                .stubMessageUpdated(MessageUpdatedExpectation.<TicketMessage>builder()
                        .description("Ticket form updated to closed")
                        .channelId(ticket.channelId())
                        .ts(ticket.formMessageTs())
                        .threadTs(ticket.queryTs())
                        .receiver(new TicketMessage.Receiver())
                        .build());
        var resolvedReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Resolved reaction")
                        .reaction("white_check_mark")
                        .channelId(ticket.channelId())
                        .ts(ticket.queryTs())
                        .build());
        var getQueryMessageStub = testKit.slack()
                .wiremock()
                .stubGetMessage(MessageToGet.builder()
                        .description("Get query message for close flow")
                        .channelId(ticket.channelId())
                        .ts(ticket.queryTs())
                        .threadTs(ticket.queryTs())
                        .text(queryText)
                        .blocksJson("[]")
                        .userId(testKit.as(tenant).userId())
                        .botId(null)
                        .build());
        var ratingRequestStub = testKit.slack()
                .wiremock()
                .stubEphemeralMessagePosted(EphemeralMessageExpectation.<RatingRequestMessage>builder()
                        .description("Rating request prompt")
                        .channelId(ticket.channelId())
                        .threadTs(ticket.queryTs())
                        .userId(testKit.as(tenant).userId())
                        .receiver(new RatingRequestMessage.Receiver(ticket.ticketId()))
                        .build());

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubClosedStub.assertIsCalled();
            closeNoticeStub.assertIsCalled();
            updateStub.assertIsCalled();
            resolvedReactionStub.assertIsCalled();
            getQueryMessageStub.assertIsCalled();
            ratingRequestStub.assertIsCalled();
        });

        var updatedTicket = supportBotClient.findTicketByQueryTs(ticket.channelId(), ticket.queryTs());
        assertThat(updatedTicket).isNotNull();
        assertThat(updatedTicket.status()).isEqualTo("closed");
        assertThat(updatedTicket.escalated()).isFalse();
    }

    @Test
    public void whenOneTrackedPrClosesButAnotherRemainsOpen_pollerDoesNotCloseTicket() {
        SeededTicket ticket = createOpenedTicket("Please review two PRs");

        Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
        supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.ticketId())
                        .githubRepo(PR_REPO)
                        .prNumber(1)
                        .prCreatedAt(prCreatedAt)
                        .slaDeadline(prCreatedAt.plus(Duration.ofHours(24)))
                        .owningTeam("wow")
                        .build());
        supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.ticketId())
                        .githubRepo(PR_REPO)
                        .prNumber(2)
                        .prCreatedAt(prCreatedAt)
                        .slaDeadline(prCreatedAt.plus(Duration.ofHours(24)))
                        .owningTeam("wow")
                        .build());

        var githubClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("Poll GitHub PR #1 closed", PR_REPO, 1, "closed", prCreatedAt.toString());
        var githubOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("Poll GitHub PR #2 open", PR_REPO, 2, "open", prCreatedAt.toString());
        var closeNoticeStub = testKit.slack().wiremock().stubChatPostMessage("Poll close notice", ticket.channelId());

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubClosedStub.assertIsCalled();
            githubOpenStub.assertIsCalled();
            closeNoticeStub.assertIsCalled();
        });

        var updatedTicket = supportBotClient.findTicketByQueryTs(ticket.channelId(), ticket.queryTs());
        assertThat(updatedTicket).isNotNull();
        assertThat(updatedTicket.status()).isEqualTo("opened");
        assertThat(updatedTicket.escalated()).isFalse();
    }

    @Test
    public void whenTrackedPrSlaBreachesAfterTracking_pollerEscalatesAndMarksTicketEscalated() {
        SeededTicket ticket = createOpenedTicket("Please review my stale PR");

        Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
        supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticket.ticketId())
                        .githubRepo(PR_REPO)
                        .prNumber(1)
                        .prCreatedAt(prCreatedAt)
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .build());

        var githubOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("Poll GitHub PR open", PR_REPO, 1, "open", prCreatedAt.toString());
        var escalatedReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Escalated reaction")
                        .reaction("rocket")
                        .channelId(ticket.channelId())
                        .ts(ticket.queryTs())
                        .build());
        var escalationMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("Escalation message", ticket.channelId());

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubOpenStub.assertIsCalled();
            escalatedReactionStub.assertIsCalled();
            escalationMessageStub.assertIsCalled();
        });

        var updatedTicket = supportBotClient.findTicketByQueryTs(ticket.channelId(), ticket.queryTs());
        assertThat(updatedTicket).isNotNull();
        assertThat(updatedTicket.status()).isEqualTo("opened");
        assertThat(updatedTicket.escalated()).isTrue();
    }

    @Test
    public void whenThreadReplyOriginTrackedPrCloses_pollerDoesNotCloseTicket() {

        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        MessageTs queryTs = MessageTs.now();
        String queryText = "Please help with this issue";
        SlackMessage tenantsQuery = asTenantSlack.postMessage(queryTs, queryText);

        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow("ticket created", ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));

        // Track PR from a thread reply (reply-origin records should not auto-close ticket on resolve).
        Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
        var githubOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("Track PR #1 open", PR_REPO, 1, "open", prCreatedAt.toString());
        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction on query")
                        .reaction("eyes")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var ticketReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Ticket reaction on query")
                        .reaction("ticket")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var slaReplyStub =
                testKit.slack().wiremock().stubChatPostMessage("PR SLA reply in thread", tenantsQuery.channelId());

        asTenantSlack.postThreadReply(MessageTs.now(), queryTs, "Fix in PR: https://github.com/" + PR_REPO + "/pull/1");

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubOpenStub.assertIsCalled();
            prReactionStub.assertIsCalled();
            eyesReactionStub.assertIsCalled();
            ticketReactionStub.assertIsCalled();
            slaReplyStub.assertIsCalled();
        });

        // Now PR is closed; poller should close tracking record but must keep ticket OPEN for reply-origin PRs.
        var githubClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("Poll PR #1 closed", PR_REPO, 1, "closed", prCreatedAt.toString());
        var closeNoticeStub =
                testKit.slack().wiremock().stubChatPostMessage("Poll close notice", tenantsQuery.channelId());
        var resolvedReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Resolved reaction should not be added")
                        .reaction("white_check_mark")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());

        supportBotClient.test().triggerPrTrackingPoll();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubClosedStub.assertIsCalled();
            closeNoticeStub.assertIsCalled();
            resolvedReactionStub.assertIsNotCalled();
        });

        var updatedTicket = supportBotClient.findTicketByQueryTs(tenantsQuery.channelId(), queryTs);
        assertThat(updatedTicket).isNotNull();
        assertThat(updatedTicket.status()).isEqualTo("opened");
        assertThat(updatedTicket.escalated()).isFalse();

        resolvedReactionStub.cleanUp();
    }

    private SeededTicket createOpenedTicket(String queryText) {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        MessageTs queryTs = MessageTs.now();
        SlackMessage tenantsQuery = asTenantSlack.postMessage(queryTs, queryText);
        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow("ticket created", ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));

        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        var ticket = supportBotClient.assertTicketExists(TicketByIdQuery.fromTicketMessage(ticketMessage, queryText));
        return new SeededTicket(ticket.id(), tenantsQuery.channelId(), queryTs, ticketMessage.ts());
    }

    private record SeededTicket(long ticketId, String channelId, MessageTs queryTs, MessageTs formMessageTs) {}

    // -------------------------------------------------------------------------
    // State machine transition tests — seed records directly, no Slack events
    // -------------------------------------------------------------------------

    @Test
    public void whenPollDetectsChangesRequestedReview_postsSlackMessageAndPausesSla() {
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

        String crReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 changes requested", PR_REPO, 1, "open", recentCreatedAt(), false, crReviewJson);
        var slackMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prStub.assertIsCalled();
        slackMessageStub.assertIsCalled();
        var updated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updated.status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(updated.slaDeadline()).isNull();
        assertThat(updated.slaRemaining()).isNotNull().isPositive();
    }

    @Test
    public void whenPollDetectsApprovalWithMergeConflicts_pausesSlaAndTransitionsToApproved() {
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

        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prStub = testKit.slack()
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

        prStub.assertIsCalled();
        unexpectedMessageStub.assertIsNotCalled();
        unexpectedMessageStub.cleanUp();
        var updated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updated.status()).isEqualTo("APPROVED");
        assertThat(updated.slaDeadline()).isNull();
        assertThat(updated.slaRemaining()).isNotNull().isPositive();
    }

    @Test
    public void whenPollDetectsApprovalAndMergeable_closesRecordDirectlyFromOpen() {
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

        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved and mergeable",
                        PR_REPO,
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

    @Test
    public void whenPollDetectsPrClosed_closesRecordAndPostsSlackMessage() {
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

        var prStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("PR #1 closed", PR_REPO, 1, "closed", recentCreatedAt(), false, "[]");
        var slackMessageStub = testKit.slack().wiremock().stubChatPostMessage("PR closed notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prStub.assertIsCalled();
        slackMessageStub.assertIsCalled();
        var updated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updated.status()).isEqualTo("CLOSED");
        assertThat(updated.closedAt()).isNotNull();
    }

    @Test
    public void whenPollDetectsSlaBreach_escalatesTicketAndPostsSlackMessage() {
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
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(25)))
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        var prStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("PR #1 open no reviews", PR_REPO, 1, "open", recentCreatedAt(), false, "[]");
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

        prStub.assertIsCalled();
        escalationMessageStub.assertIsCalled();
        rocketReactionStub.assertIsCalled();
        var updated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updated.status()).isEqualTo("ESCALATED");
        assertThat(updated.escalationId()).isNotNull();
    }

    @Test
    public void whenChangesRequestedAndAllReviewsDismissed_resumesSlaAndTransitionsToOpen() {
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

        // Poll 1: drive record to CHANGES_REQUESTED
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
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("CHANGES_REQUESTED");

        // Poll 2: reviews dismissed → back to OPEN, SLA resumed
        var prNoReviewsStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("PR #1 no reviews", PR_REPO, 1, "open", recentCreatedAt(), false, "[]");
        var unexpectedMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prNoReviewsStub.assertIsCalled();
        unexpectedMessageStub.assertIsNotCalled();
        unexpectedMessageStub.cleanUp();
        var resumed = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(resumed.status()).isEqualTo("OPEN");
        assertThat(resumed.slaDeadline()).isNotNull();
        assertThat(resumed.slaRemaining()).isNull();
    }

    @Test
    public void whenChangesRequestedAndReviewerApproves_transitionsToApproved() {
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

        // Poll 1: drive to CHANGES_REQUESTED
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
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("CHANGES_REQUESTED");

        // Poll 2: reviewer approves, merge conflicts still present → APPROVED (SLA remains paused)
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
        var result = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.slaDeadline()).isNull();
        assertThat(result.slaRemaining()).isNotNull().isPositive();
    }

    @Test
    public void whenApprovedPrBecomesMergeable_closesRecordAndPostsSlackMessage() {
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

        // Poll 1: drive to APPROVED (approved, merge conflicts)
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prApprovedNotMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved not mergeable",
                        PR_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        false,
                        approvedReviewJson);
        var unexpectedApprovalMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected approval notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prApprovedNotMergeableStub.assertIsCalled();
        unexpectedApprovalMessageStub.assertIsNotCalled();
        unexpectedApprovalMessageStub.cleanUp();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("APPROVED");

        // Poll 2: conflicts resolved → CLOSED
        var prMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved mergeable", PR_REPO, 1, "open", recentCreatedAt(), true, "[]");
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved and ready to merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prMergeableStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var updated = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updated.status()).isEqualTo("CLOSED");
        assertThat(updated.closedAt()).isNotNull();
    }

    @Test
    public void whenApprovedAndNewChangesRequested_transitionsToChangesRequested() {
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

        // Poll 1: drive to APPROVED (approved, merge conflicts)
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
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
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("APPROVED");

        // Poll 2: second reviewer requests changes → CHANGES_REQUESTED + Slack notification
        String crReviewJson = """
                [{"id":2,"user":{"login":"reviewer2"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T11:00:00Z","body":""}]
                """;
        var prWithCrStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 changes requested", PR_REPO, 1, "open", recentCreatedAt(), false, crReviewJson);
        var crMessageStub = testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prWithCrStub.assertIsCalled();
        crMessageStub.assertIsCalled();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("CHANGES_REQUESTED");
    }

    @Test
    public void whenEscalatedAndPrApprovedAndMergeable_closesRecord() {
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
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(25)))
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: SLA breached → ESCALATED
        var prOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("PR #1 open no reviews", PR_REPO, 1, "open", recentCreatedAt(), false, "[]");
        var escalationMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("escalation notification", channelId);
        var rocketReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("rocket reaction")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        supportBotClient.test().triggerPrTrackingPoll();

        prOpenStub.assertIsCalled();
        escalationMessageStub.assertIsCalled();
        rocketReactionStub.assertIsCalled();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("ESCALATED");

        // Poll 2: approved and mergeable → CLOSED, escalationId preserved
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prApprovedMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved and mergeable",
                        PR_REPO,
                        1,
                        "open",
                        recentCreatedAt(),
                        true,
                        approvedReviewJson);
        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved and ready to merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prApprovedMergeableStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var result = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.closedAt()).isNotNull();
        assertThat(result.escalationId()).isNotNull();
    }

    @Test
    public void whenEscalatedAndPrApprovedButNotMergeable_transitionsToApproved() {
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
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(25)))
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: SLA breached → ESCALATED
        var prOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("PR #1 open no reviews", PR_REPO, 1, "open", recentCreatedAt(), false, "[]");
        var escalationMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("escalation notification", channelId);
        var rocketReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("rocket reaction")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        supportBotClient.test().triggerPrTrackingPoll();

        prOpenStub.assertIsCalled();
        escalationMessageStub.assertIsCalled();
        rocketReactionStub.assertIsCalled();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("ESCALATED");

        // Poll 2: approved but merge conflicts → APPROVED (no Slack, escalationId preserved)
        String approvedReviewJson = """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
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
        var result = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.escalationId()).isNotNull();
    }

    @Test
    public void whenEscalatedAndNewChangesRequested_transitionsToChangesRequested() {
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
                        .prCreatedAt(Instant.now().minus(Duration.ofHours(25)))
                        .slaDeadline(Instant.now().minus(Duration.ofHours(1)))
                        .owningTeam("wow")
                        .canAutoCloseTicket(false)
                        .build());

        // Poll 1: SLA breached → ESCALATED
        var prOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("PR #1 open no reviews", PR_REPO, 1, "open", recentCreatedAt(), false, "[]");
        var escalationMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("escalation notification", channelId);
        var rocketReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("rocket reaction")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        supportBotClient.test().triggerPrTrackingPoll();

        prOpenStub.assertIsCalled();
        escalationMessageStub.assertIsCalled();
        rocketReactionStub.assertIsCalled();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("ESCALATED");

        // Poll 2: reviewer requests changes → CHANGES_REQUESTED + Slack notification
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
        var result = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(result.status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(result.escalationId()).isNotNull();
    }

    /**
     * Lifecycle poller tests for GitLab MR records. Seed a record with
     * {@code provider=gitlab} and then drive transitions via the GitLab v4 API stubs.
     */
    @Nested
    public class GitLab {

        private static final String MR_REPO = "gitlab-org/gitlab-pr-test-repo";

        @Test
        public void whenPollDetectsApprovalAndMergeable_closesGitLabRecord() {
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
            // Approved + mergeable on an open MR should drive the record straight to CLOSED.
            var mrStub = testKit.slack()
                    .wiremock()
                    .stubGitLabGetMergeRequest(
                            "MR !42 approved mergeable", MR_REPO, 42, "opened", "mergeable", createdAt, createdAt);
            var approvalsStub = testKit.slack()
                    .wiremock()
                    .stubGitLabGetMergeRequestApprovals("MR !42 approvals", MR_REPO, 42, List.of("reviewer"));
            // The team-reviewer cache pulls the gitlab-group-path members; gitlab-org includes
            // "reviewer" so the approval counts as a team review.
            var groupStub = testKit.slack()
                    .wiremock()
                    .stubGitLabGetGroupMembers("gitlab-org group members", "gitlab-org", List.of("reviewer"));
            var closeMessageStub =
                    testKit.slack().wiremock().stubChatPostMessage("approved + mergeable notification", channelId);

            supportBotClient.test().triggerPrTrackingPoll();

            await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
                mrStub.assertIsCalled();
                approvalsStub.assertIsCalled();
                groupStub.assertIsCalled();
                closeMessageStub.assertIsCalled();
            });
            var result = supportBotClient.test().getPrTrackingRecord(record.id());
            assertThat(result.status()).isEqualTo("CLOSED");
            assertThat(result.closedAt()).isNotNull();
        }

        @Test
        public void whenPollDetectsSlaBreachOnGitLabMr_escalatesAndPostsSlackMessage() {
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
                            .build());

            String createdAt = Instant.now().minus(Duration.ofHours(25)).toString();
            var mrStub = testKit.slack()
                    .wiremock()
                    .stubGitLabGetMergeRequest(
                            "MR !42 open no approvals", MR_REPO, 42, "opened", "mergeable", createdAt, createdAt);
            var approvalsStub = testKit.slack()
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
                mrStub.assertIsCalled();
                approvalsStub.assertIsCalled();
                escalationMessageStub.assertIsCalled();
                rocketReactionStub.assertIsCalled();
            });
            var updated = supportBotClient.test().getPrTrackingRecord(record.id());
            assertThat(updated.status()).isEqualTo("ESCALATED");
            assertThat(updated.escalationId()).isNotNull();
        }
    }
}
