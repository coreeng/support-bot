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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
public class PrLifecyclePollerFunctionalTests {
    private static final String PR_REPO = "test-org/pr-test-repo";

    private TestKit testKit;
    private SupportBotClient supportBotClient;

    @Test
    public void whenTrackedPrClosesAfterInitialTracking_pollerClosesTicketIfLastActivePr() {
        String queryText = "Please review my PR";
        supportBotClient.test().cleanupPrTrackingRecords();
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
        supportBotClient.test().cleanupPrTrackingRecords();
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
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    @Test
    public void whenTrackedPrSlaBreachesAfterTracking_pollerEscalatesAndMarksTicketEscalated() {
        supportBotClient.test().cleanupPrTrackingRecords();
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
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    @Test
    public void whenThreadReplyOriginTrackedPrCloses_pollerDoesNotCloseTicket() {
        supportBotClient.test().cleanupPrTrackingRecords();

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
        supportBotClient.test().cleanupPrTrackingRecords();
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
}
