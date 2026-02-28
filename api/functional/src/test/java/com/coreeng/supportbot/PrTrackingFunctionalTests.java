package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coreeng.supportbot.testkit.MessageTs;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Functional tests for PR review tracking (pr-review-tracking feature).
 *
 * <p>Requires the app to run with profile {@code functionaltests} so that
 * pr-review-tracking is enabled and {@code GITHUB_API_BASE_URL} points at the
 * wiremock (e.g. {@code http://localhost:8000}).
 */
@ExtendWith(TestKitExtension.class)
public class PrTrackingFunctionalTests {

    private static final String PR_REPO = "test-org/pr-test-repo";
    private static final String PR_LINK_1 = "https://github.com/" + PR_REPO + "/pull/1";
    private static final String PR_LINK_2 = "https://github.com/" + PR_REPO + "/pull/2";

    /** Returns a PR created-at timestamp 1 hour ago — safely within the 24h SLA window. */
    private static String recentCreatedAt() {
        return java.time.Instant.now().minus(Duration.ofHours(1)).toString();
    }

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @Test
    public void whenSamePrIsPostedAgainOnSameTicket_itIsNotTrackedTwice() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        MessageTs queryTs = MessageTs.now();
        String queryText = "Please help with my issue";
        SlackMessage tenantsQuery = asTenantSlack.postMessage(queryTs, queryText);

        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow("ticket created", ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));

        var githubStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #1", PR_REPO, 1, "open", recentCreatedAt());
        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var slaReplyStub = testKit.slack().wiremock().stubChatPostMessage(
                "PR SLA reply in thread", tenantsQuery.channelId());

        // First PR link should be tracked.
        asTenantSlack.postThreadReply(MessageTs.now(), queryTs, "Here is the fix: " + PR_LINK_1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            githubStub.assertIsCalled();
            prReactionStub.assertIsCalled();
            slaReplyStub.assertIsCalled();
        });

        // Posting the same PR link again for the same ticket should be ignored.
        var duplicateGithubStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #1 duplicate should not be fetched", PR_REPO, 1, "open", recentCreatedAt());
        var duplicatePrReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction should not be added for duplicate")
                        .reaction("pr")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var duplicateSlaReplyStub = testKit.slack().wiremock().stubChatPostMessage(
                "PR SLA reply should not be posted for duplicate", tenantsQuery.channelId());

        asTenantSlack.postThreadReply(MessageTs.now(), queryTs, "Re-posting same PR: " + PR_LINK_1);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            duplicateGithubStub.assertIsNotCalled();
            duplicatePrReactionStub.assertIsNotCalled();
            duplicateSlaReplyStub.assertIsNotCalled();
            assertThat(supportBotClient.findTicketByQueryTs(tenantsQuery.channelId(), queryTs)).isNotNull();
        });

        duplicateGithubStub.cleanUp();
        duplicatePrReactionStub.cleanUp();
        duplicateSlaReplyStub.cleanUp();
    }

    @Test
    public void whenMessageContainsOneClosedAndOneOpenPr_ticketStaysOpenAndOnlyOpenPrTracked() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String messageWithTwoPrs = "Please review " + PR_LINK_1 + " and " + PR_LINK_2;
        MessageTs ticketMessageTs = MessageTs.now();

        var githubClosedStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #1 closed", PR_REPO, 1, "closed", recentCreatedAt());
        var githubOpenStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #2 open", PR_REPO, 2, "open", recentCreatedAt());

        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        // Must be registered after generic chat.postMessage stubs (Wiremock LIFO matching).
        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        asTenantSlack.postMessage(queryTs, messageWithTwoPrs);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubClosedStub.assertIsCalled();
            githubOpenStub.assertIsCalled();
            prReactionStub.assertIsCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();
        assertThat(ticketResponse.status()).isEqualTo("opened");
        assertThat(ticketResponse.escalated()).isFalse();
        creationStubs.cleanUp();
    }

    @Test
    public void whenOnePrLookupFailsAndAnotherSucceeds_systemStillTracksSuccessfulPr() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String messageWithTwoPrs = "Please review " + PR_LINK_1 + " and " + PR_LINK_2;
        MessageTs ticketMessageTs = MessageTs.now();

        var githubErrorStub = testKit.slack().wiremock().stubGitHubGetPullRequestError(
                "GitHub PR #1 error", PR_REPO, 1, 404, "PR not found");
        var githubOpenStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #2 open", PR_REPO, 2, "open", recentCreatedAt());

        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        // Must be registered after generic chat.postMessage stubs (Wiremock LIFO matching).
        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        asTenantSlack.postMessage(queryTs, messageWithTwoPrs);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            githubErrorStub.assertIsCalled();
            githubOpenStub.assertIsCalled();
            prReactionStub.assertIsCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();
        assertThat(ticketResponse.status()).isEqualTo("opened");
        assertThat(ticketResponse.escalated()).isFalse();
        creationStubs.cleanUp();
    }

    @Test
    public void whenPrLinkPostedAsThreadReply_ticketTracksPrAndPostsSlaReply() {
        // given — create a ticket first (support reacts with eyes to query)
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        SlackTestKit asSupportSlack = testKit.as(support).slack();

        MessageTs queryTs = MessageTs.now();
        String queryText = "Please help with my issue";
        SlackMessage tenantsQuery = asTenantSlack.postMessage(queryTs, queryText);

        MessageTs ticketMessageTs = MessageTs.now();
        var creationStubs = tenantsQuery.stubTicketCreationFlow("ticket created", ticketMessageTs);
        asSupportSlack.addReactionTo(tenantsQuery, "eyes");
        creationStubs.awaitAllCalled(Duration.ofSeconds(5));

        TicketMessage ticketMessage = creationStubs.ticketMessagePosted().result();
        assertThat(ticketMessage).isNotNull();
        supportBotClient.assertTicketExists(TicketByIdQuery.fromTicketMessage(ticketMessage, queryText));

        // stub GitHub: PR is open
        var githubStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #1", PR_REPO, 1, "open", recentCreatedAt());
        // stub Slack: PR emoji on query message, then SLA reply in thread
        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var slaReplyStub = testKit.slack().wiremock().stubChatPostMessage(
                "PR SLA reply in thread", tenantsQuery.channelId());

        // when — tenant posts a thread reply with a PR link
        MessageTs replyTs = MessageTs.now();
        asTenantSlack.postThreadReply(replyTs, queryTs, "Here is the fix: " + PR_LINK_1);

        // then — app detects PR, calls GitHub, adds reaction and posts SLA reply
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            prReactionStub.assertIsCalled();
            slaReplyStub.assertIsCalled();
        });
        githubStub.cleanUp();
    }

    @Test
    public void whenTwoPrLinksPostedAsOriginalMessage_bothTrackedAndTicketCreated() {
        // given — set up all stubs before posting (app will create ticket and run PR detection on the same event)
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String messageWithTwoPrs = "Could you review " + PR_LINK_1 + " and " + PR_LINK_2 + "?";
        MessageTs ticketMessageTs = MessageTs.now();


        // Stub GitHub for both PRs (open)
        var githubStub1 = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #1", PR_REPO, 1, "open", recentCreatedAt());
        var githubStub2 = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR #2", PR_REPO, 2, "open", recentCreatedAt());

        // Stub Slack: PR reactions on the query message.
        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        // Stub ticket creation for the initial query message.
        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        // when — post original message with two PR links
        asTenantSlack.postMessage(queryTs, messageWithTwoPrs);

        // then — app creates ticket and processes both PR links via GitHub.
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            creationStubs.conversationsReplies().assertIsNotCalled();
            creationStubs.reactionAdded().assertIsCalled();
            githubStub1.assertIsCalled();
            githubStub2.assertIsCalled();
            prReactionStub.assertIsCalled(2);
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();

        creationStubs.cleanUp();
    }

    @Test
    public void whenPrIsClosed_detectionSkipsTrackingAndKeepsTicketOpened() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String messageWithPr = "Could you review " + PR_LINK_1 + "?";
        MessageTs ticketMessageTs = MessageTs.now();

        // Stub GitHub (PR is closed => detection should skip tracking entirely)
        var githubStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR closed", PR_REPO, 1, "closed", recentCreatedAt());

        // Ticket creation still happens for query messages with PR links.
        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        // PR-specific side effects should not happen for closed PR links.
        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("No PR reaction expected")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        // when — post original message with one PR link
        asTenantSlack.postMessage(queryTs, messageWithPr);

        // then — ticket exists and remains opened; no PR tracking side effects.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            githubStub.assertIsCalled();
            creationStubs.reactionAdded().assertIsCalled();
            creationStubs.ticketMessagePosted().assertIsCalled();
            creationStubs.conversationsReplies().assertIsNotCalled();
            prReactionStub.assertIsNotCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();
        assertThat(ticketResponse.status()).isEqualTo("opened");
        assertThat(ticketResponse.escalated()).isFalse();

        githubStub.cleanUp();
        creationStubs.cleanUp();
        prReactionStub.cleanUp();
    }

    @Test
    public void whenSlaAlreadyBreachedAtDetectionTime_escalatesImmediately() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String messageWithPr = "Could you review " + PR_LINK_1 + "?";
        MessageTs ticketMessageTs = MessageTs.now();

        // Stub GitHub (PR created 2 days ago, breaking the 24h SLA)
        Instant oldCreatedAt = Instant.now().minus(Duration.ofDays(2));
        var githubStub = testKit.slack().wiremock().stubGitHubGetPullRequest(
                "GitHub PR open", PR_REPO, 1, "open", oldCreatedAt.toString());

        // Stub reactions
        var prReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("PR reaction")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        var escalatedReactionStub = testKit.slack().wiremock().stubReactionAdd(
                ReactionAddedExpectation.builder()
                        .description("Escalated reaction")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        // Stub ticket creation.
        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        // when
        SlackMessage tenantsMessage = asTenantSlack.postMessage(queryTs, messageWithPr);

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            prReactionStub.assertIsCalled();
            escalatedReactionStub.assertIsCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();
        assertThat(ticketResponse.status()).isEqualTo("opened");
        assertThat(ticketResponse.escalated()).isTrue();

        githubStub.cleanUp();
        creationStubs.cleanUp();
    }

    @Test
    public void whenPrForUnconfiguredRepoPosted_ignored() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String unconfiguredRepo = "unconfigured-org/unconfigured-repo";
        String messageWithPr = "Could you review https://github.com/" + unconfiguredRepo + "/pull/1 ?";
        MessageTs ticketMessageTs = MessageTs.now();

        // Stub ticket creation - if it works as intended (IGNORED), these should NOT be called.
        // NOTE: Currently there might be a bug where it tries to create a ticket anyway.
        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ignored ticket", ticketMessageTs);

        // when
        SlackMessage tenantsMessage = asTenantSlack.postMessage(queryTs, messageWithPr);

        // then — ensure it remains ignored for a stable window
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                creationStubs.ticketMessagePosted().assertIsNotCalled());

        creationStubs.cleanUp();
    }
}
