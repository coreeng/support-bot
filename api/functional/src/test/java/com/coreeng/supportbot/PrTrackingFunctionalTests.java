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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

    private static final String SLA_FILE_REPO = "test-org/pr-sla-file-repo";
    private static final String SLA_FILE_PR = "https://github.com/" + SLA_FILE_REPO + "/pull/101";
    private static final String SLA_OVERRIDE_REPO = "test-org/pr-sla-override-repo";
    private static final String SLA_OVERRIDE_PR = "https://github.com/" + SLA_OVERRIDE_REPO + "/pull/201";

    /** Returns a PR created-at timestamp 1 hour ago — safely within the 24h SLA window. */
    private static String recentCreatedAt() {
        return java.time.Instant.now().minus(Duration.ofHours(1)).toString();
    }

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanUpPrTrackingRecords() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

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

        var githubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #1", PR_REPO, 1, "open", recentCreatedAt());
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

        // First PR link should be tracked.
        asTenantSlack.postThreadReply(MessageTs.now(), queryTs, "Here is the fix: " + PR_LINK_1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            githubStub.assertIsCalled();
            prReactionStub.assertIsCalled();
            eyesReactionStub.assertIsCalled();
            ticketReactionStub.assertIsCalled();
            slaReplyStub.assertIsCalled();
        });

        // Posting the same PR link again for the same ticket should be ignored.
        var duplicateGithubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "GitHub PR #1 duplicate should not be fetched", PR_REPO, 1, "open", recentCreatedAt());
        var duplicatePrReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction should not be added for duplicate")
                        .reaction("pr")
                        .channelId(tenantsQuery.channelId())
                        .ts(queryTs)
                        .build());
        var duplicateSlaReplyStub = testKit.slack()
                .wiremock()
                .stubChatPostMessage("PR SLA reply should not be posted for duplicate", tenantsQuery.channelId());

        asTenantSlack.postThreadReply(MessageTs.now(), queryTs, "Re-posting same PR: " + PR_LINK_1);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            duplicateGithubStub.assertIsNotCalled();
            duplicatePrReactionStub.assertIsNotCalled();
            duplicateSlaReplyStub.assertIsNotCalled();
            assertThat(supportBotClient.findTicketByQueryTs(tenantsQuery.channelId(), queryTs))
                    .isNotNull();
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

        var githubClosedStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #1 closed", PR_REPO, 1, "closed", recentCreatedAt());
        var githubOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #2 open", PR_REPO, 2, "open", recentCreatedAt());

        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction on query")
                        .reaction("eyes")
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
            eyesReactionStub.assertIsCalled();
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

        var githubErrorStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequestError("GitHub PR #1 error", PR_REPO, 1, 404, "PR not found");
        var githubOpenStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #2 open", PR_REPO, 2, "open", recentCreatedAt());

        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction on query")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction on query")
                        .reaction("eyes")
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
            eyesReactionStub.assertIsCalled();
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
        var githubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #1", PR_REPO, 1, "open", recentCreatedAt());
        // stub Slack: PR emoji on query message, then SLA reply in thread
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

        // when — tenant posts a thread reply with a PR link
        MessageTs replyTs = MessageTs.now();
        asTenantSlack.postThreadReply(replyTs, queryTs, "Here is the fix: " + PR_LINK_1);

        // then — app detects PR, calls GitHub, adds reaction and posts SLA reply
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            prReactionStub.assertIsCalled();
            eyesReactionStub.assertIsCalled();
            ticketReactionStub.assertIsCalled();
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
        var githubStub1 = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #1", PR_REPO, 1, "open", recentCreatedAt());
        var githubStub2 = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #2", PR_REPO, 2, "open", recentCreatedAt());

        // Stub Slack: PR reactions on the query message.
        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction")
                        .reaction("eyes")
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
            eyesReactionStub.assertIsCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();

        creationStubs.cleanUp();
    }

    @Test
    public void whenPrIsClosedInOriginalMessage_detectionSkipsTrackingAndDoesNotCreateTicket() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        String messageWithPr = "Could you review " + PR_LINK_1 + "?";

        // Stub GitHub (PR is closed => detection should skip tracking entirely)
        var githubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR closed", PR_REPO, 1, "closed", recentCreatedAt());

        // PR-specific side effects should not happen for closed PR links.
        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("No PR reaction expected")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction expected")
                        .reaction("eyes")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        // when — post original message with one PR link
        asTenantSlack.postMessage(queryTs, messageWithPr);

        // then — no ticket is created and no PR tracking side effects happen.
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            githubStub.assertIsCalled();
            prReactionStub.assertIsNotCalled();
            eyesReactionStub.assertIsNotCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNull();

        githubStub.cleanUp();
        prReactionStub.cleanUp();
        eyesReactionStub.cleanUp();
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
        var githubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR open", PR_REPO, 1, "open", oldCreatedAt.toString());

        // Stub reactions
        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction")
                        .reaction("eyes")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        var escalatedReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
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
            eyesReactionStub.assertIsCalled();
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
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> creationStubs.ticketMessagePosted().assertIsNotCalled());

        creationStubs.cleanUp();
    }

    @Test
    public void whenSlaFileExistsInRepo_usesFileSlaInsteadOfInlineDefault() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        MessageTs ticketMessageTs = MessageTs.now();

        // PR created 2 minutes ago
        Instant oldCreatedAt = Instant.now().minus(Duration.ofMinutes(2));
        var githubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #101", SLA_FILE_REPO, 101, "open", oldCreatedAt.toString());

        // SLA file returns 1 minute default, much shorter than the 24h inline default
        var fileStub = testKit.slack()
                .wiremock()
                .stubGitHubGetFileContent("SLA file for file repo", SLA_FILE_REPO, ".pr-sla.yaml", "default: PT1M");

        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction")
                        .reaction("eyes")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var escalatedReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Escalated reaction")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        // when
        asTenantSlack.postMessage(queryTs, "Please review " + SLA_FILE_PR);

        // then, file SLA of 1 minute is used so PR is already in breach
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            githubStub.assertIsCalled();
            fileStub.assertIsCalled();
            prReactionStub.assertIsCalled();
            eyesReactionStub.assertIsCalled();
            escalatedReactionStub.assertIsCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();
        assertThat(ticketResponse.escalated()).isTrue();

        creationStubs.cleanUp();
    }

    @Test
    public void whenPrFilesMatchOverrideInSlaFile_usesOverrideDuration() {
        TestKit.RoledTestKit asTenant = testKit.as(tenant);
        SlackTestKit asTenantSlack = asTenant.slack();
        String channelId = testKit.config().mocks().slack().supportChannelId();

        MessageTs queryTs = MessageTs.now();
        MessageTs ticketMessageTs = MessageTs.now();

        // PR created 2 minutes ago
        Instant oldCreatedAt = Instant.now().minus(Duration.ofMinutes(2));
        var githubStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest("GitHub PR #201", SLA_OVERRIDE_REPO, 201, "open", oldCreatedAt.toString());

        // SLA file has a long default but a short override for docs/**
        String slaYaml = "default: PT24H\noverrides:\n  - path: \"docs/**\"\n    sla: PT1M";
        var fileStub = testKit.slack()
                .wiremock()
                .stubGitHubGetFileContent("SLA file for override repo", SLA_OVERRIDE_REPO, ".pr-sla.yaml", slaYaml);

        // PR touches docs/README.md which matches the override
        var filesStub = testKit.slack()
                .wiremock()
                .stubGitHubListPullRequestFiles("PR #201 files", SLA_OVERRIDE_REPO, 201, List.of("docs/README.md"));

        var prReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("PR reaction")
                        .reaction("pr")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var eyesReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Eyes reaction")
                        .reaction("eyes")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());
        var escalatedReactionStub = testKit.slack()
                .wiremock()
                .stubReactionAdd(ReactionAddedExpectation.builder()
                        .description("Escalated reaction")
                        .reaction("rocket")
                        .channelId(channelId)
                        .ts(queryTs)
                        .build());

        SlackMessage messageForStubs = SlackMessage.builder()
                .slackWiremock(testKit.slack().wiremock())
                .ts(queryTs)
                .channelId(channelId)
                .build();
        var creationStubs = messageForStubs.stubTicketCreationFlow("ticket created", ticketMessageTs);

        // when
        asTenantSlack.postMessage(queryTs, "Please review " + SLA_OVERRIDE_PR);

        // then, override SLA of 1 minute for docs/** is used so PR is already in breach
        // githubStub is called twice: once by getPullRequest, once by listPullRequestFiles
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            githubStub.assertIsCalled(2);
            fileStub.assertIsCalled();
            filesStub.assertIsCalled();
            prReactionStub.assertIsCalled();
            eyesReactionStub.assertIsCalled();
            escalatedReactionStub.assertIsCalled();
        });

        var ticketResponse = supportBotClient.findTicketByQueryTs(channelId, queryTs);
        assertThat(ticketResponse).isNotNull();
        assertThat(ticketResponse.escalated()).isTrue();

        creationStubs.cleanUp();
    }

    @Test
    public void whenPollDetectsChangesRequestedReview_postsSlackMessageAndPausesSla() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
                .channelId(channelId)
                .queryTs(queryTs)
                .createdMessageTs(ticketTs)
                .build());

        var record = supportBotClient.test().createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                .ticketId(ticket.id())
                .githubRepo(PR_REPO)
                .prNumber(1)
                .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                .owningTeam("wow")
                .canAutoCloseTicket(false)
                .build());

        String crReviewJson =
                """
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
        var updatedRecord = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updatedRecord.status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(updatedRecord.slaDeadline()).isNull();
        assertThat(updatedRecord.slaRemaining()).isNotNull().isPositive();
    }

    @Test
    public void whenPollDetectsApprovalWithMergeConflicts_pausesSlaAndTransitionsToApproved() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
                .channelId(channelId)
                .queryTs(queryTs)
                .createdMessageTs(ticketTs)
                .build());

        var record = supportBotClient.test().createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                .ticketId(ticket.id())
                .githubRepo(PR_REPO)
                .prNumber(1)
                .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                .owningTeam("wow")
                .canAutoCloseTicket(false)
                .build());

        String approvedReviewJson =
                """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved not mergeable", PR_REPO, 1, "open", recentCreatedAt(), false, approvedReviewJson);

        var unexpectedMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prStub.assertIsCalled();
        unexpectedMessageStub.assertIsNotCalled();
        unexpectedMessageStub.cleanUp();
        var updatedRecord = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updatedRecord.status()).isEqualTo("APPROVED");
        assertThat(updatedRecord.slaDeadline()).isNull();
        assertThat(updatedRecord.slaRemaining()).isNotNull().isPositive();
    }

    @Test
    public void whenPollDetectsPrClosed_closesRecordAndPostsSlackMessage() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
                .channelId(channelId)
                .queryTs(queryTs)
                .createdMessageTs(ticketTs)
                .build());

        var record = supportBotClient.test().createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
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

        var slackMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("PR closed notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prStub.assertIsCalled();
        slackMessageStub.assertIsCalled();
        var updatedRecord = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updatedRecord.status()).isEqualTo("CLOSED");
        assertThat(updatedRecord.closedAt()).isNotNull();
    }

    @Test
    public void whenPollDetectsSlaBreach_escalatesTicketAndPostsSlackMessage() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
                .channelId(channelId)
                .queryTs(queryTs)
                .createdMessageTs(ticketTs)
                .build());

        var record = supportBotClient.test().createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
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
        var updatedRecord = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updatedRecord.status()).isEqualTo("ESCALATED");
        assertThat(updatedRecord.escalationId()).isNotNull();
    }

    @Test
    public void whenChangesRequestedAndAllReviewsDismissed_resumesSlaAndTransitionsToOpen() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
                .channelId(channelId)
                .queryTs(queryTs)
                .createdMessageTs(ticketTs)
                .build());

        var record = supportBotClient.test().createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                .ticketId(ticket.id())
                .githubRepo(PR_REPO)
                .prNumber(1)
                .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                .owningTeam("wow")
                .canAutoCloseTicket(false)
                .build());

        // Poll 1: drive record to CHANGES_REQUESTED
        String crReviewJson =
                """
                [{"id":1,"user":{"login":"reviewer"},"state":"CHANGES_REQUESTED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prWithCrStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 changes requested", PR_REPO, 1, "open", recentCreatedAt(), false, crReviewJson);
        var crMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("changes requested notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prWithCrStub.assertIsCalled();
        crMessageStub.assertIsCalled();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status())
                .isEqualTo("CHANGES_REQUESTED");

        // Poll 2: reviews gone (dismissed) → back to OPEN
        var prNoReviewsStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 no reviews", PR_REPO, 1, "open", recentCreatedAt(), false, "[]");

        var unexpectedMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prNoReviewsStub.assertIsCalled();
        unexpectedMessageStub.assertIsNotCalled();
        unexpectedMessageStub.cleanUp();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status()).isEqualTo("OPEN");
    }

    @Test
    public void whenApprovedPrBecomesMergeable_closesRecordAndPostsSlackMessage() {
        String channelId = testKit.config().mocks().slack().supportChannelId();
        MessageTs queryTs = MessageTs.now();
        MessageTs ticketTs = MessageTs.now();

        var ticket = supportBotClient.test().createTicket(SupportBotClient.TicketToCreateRequest.builder()
                .channelId(channelId)
                .queryTs(queryTs)
                .createdMessageTs(ticketTs)
                .build());

        var record = supportBotClient.test().createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                .ticketId(ticket.id())
                .githubRepo(PR_REPO)
                .prNumber(1)
                .prCreatedAt(Instant.now().minus(Duration.ofHours(1)))
                .slaDeadline(Instant.now().plus(Duration.ofHours(23)))
                .owningTeam("wow")
                .canAutoCloseTicket(false)
                .build());

        // Poll 1: drive record to APPROVED (approved but not yet mergeable)
        String approvedReviewJson =
                """
                [{"id":1,"user":{"login":"reviewer"},"state":"APPROVED","submitted_at":"2024-01-15T10:00:00Z","body":""}]
                """;
        var prApprovedNotMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved not mergeable", PR_REPO, 1, "open", recentCreatedAt(), false, approvedReviewJson);
        var unexpectedApprovalMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("unexpected approval notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prApprovedNotMergeableStub.assertIsCalled();
        unexpectedApprovalMessageStub.assertIsNotCalled();
        unexpectedApprovalMessageStub.cleanUp();
        assertThat(supportBotClient.test().getPrTrackingRecord(record.id()).status()).isEqualTo("APPROVED");

        // Poll 2: PR is now mergeable → CLOSED
        var prMergeableStub = testKit.slack()
                .wiremock()
                .stubGitHubGetPullRequest(
                        "PR #1 approved mergeable", PR_REPO, 1, "open", recentCreatedAt(), true, "[]");

        var closeMessageStub =
                testKit.slack().wiremock().stubChatPostMessage("approved and ready to merge notification", channelId);

        supportBotClient.test().triggerPrTrackingPoll();

        prMergeableStub.assertIsCalled();
        closeMessageStub.assertIsCalled();
        var updatedRecord = supportBotClient.test().getPrTrackingRecord(record.id());
        assertThat(updatedRecord.status()).isEqualTo("CLOSED");
        assertThat(updatedRecord.closedAt()).isNotNull();
    }
}
