package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.PrTrackingRepositoryProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.escalation.CreateEscalationRequest;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.prtracking.PrDetectionOutcome;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrDetectionServiceTest {

    private static final String CHANNEL_ID = "C_SUPPORT";
    private static final MessageTs QUERY_TS = MessageTs.of("1700000000.000001");
    private static final String REPO = "my-org/my-repo";
    private static final int PR_NUMBER = 42;
    private static final String TEAM_CODE = "infra-team";
    private static final String TEAM_LABEL = "Infra Team";
    private static final String PR_EMOJI = "pr";
    private static final Duration SLA_24H = Duration.ofHours(24);

    @Mock private GitHubPrUrlParser prUrlParser;
    @Mock private GitHubClient gitHubClient;
    @Mock private PrTrackingRepository prTrackingRepository;
    @Mock private PrTrackingProps prTrackingProps;
    @Mock private EscalationTeamsRegistry escalationTeamsRegistry;
    @Mock private EscalationProcessingService escalationProcessingService;
    @Mock private TicketSlackService ticketSlackService;
    @Mock private SlackClient slackClient;

    @Captor private ArgumentCaptor<SlackPostMessageRequest> postMessageCaptor;
    @Captor private ArgumentCaptor<NewPrTracking> newTrackingCaptor;

    private PrDetectionService service;

    @BeforeEach
    void setUp() {
        service = new PrDetectionService(
                prUrlParser, gitHubClient, prTrackingRepository, prTrackingProps,
                escalationTeamsRegistry, escalationProcessingService, ticketSlackService,
                slackClient);
    }

    // -------------------------------------------------------------------------
    // containsPrLinks
    // -------------------------------------------------------------------------

    @Nested
    class ContainsPrLinks {

        @Test
        void returnsTrueWhenParserFindsLinks() {
            // given
            when(prUrlParser.parse("some message")).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));

            // when
            boolean result = service.containsPrLinks("some message");

            // then
            assertThat(result).isTrue();
        }

        @Test
        void returnsFalseWhenParserFindsNoLinks() {
            // given
            when(prUrlParser.parse("no links here")).thenReturn(List.of());

            // when
            boolean result = service.containsPrLinks("no links here");

            // then
            assertThat(result).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — no PR links
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePosted_NoLinks {

        @Test
        void doesNothingWhenNoLinksDetected() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of());
            MessagePosted event = messagePostedWith("nothing interesting here");
            Ticket ticket = ticketWithId(10L);

            // when
            service.handleMessagePosted(event, ticket);

            // then
            verifyNoInteractions(gitHubClient, prTrackingRepository, slackClient, ticketSlackService, escalationProcessingService);
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — happy path (SLA not yet breached)
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePosted_HappyPath {

        @BeforeEach
        void setUpHappyPathStubs() {
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
        }

        @Test
        void insertsTrackingRecordWithCorrectFields() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            setupDetectedPr(prCreatedAt);
            Ticket ticket = ticketWithId(99L);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then
            verify(prTrackingRepository).insert(newTrackingCaptor.capture());
            NewPrTracking inserted = newTrackingCaptor.getValue();
            assertThat(inserted.ticketId()).isEqualTo(99L);
            assertThat(inserted.githubRepo()).isEqualTo(REPO);
            assertThat(inserted.prNumber()).isEqualTo(PR_NUMBER);
            assertThat(inserted.prCreatedAt()).isEqualTo(prCreatedAt);
            assertThat(inserted.slaDeadline()).isEqualTo(prCreatedAt.plus(SLA_24H));
            assertThat(inserted.owningTeam()).isEqualTo(TEAM_CODE);
        }

        @Test
        void postsSlaReplyToCorrectChannelAndThread() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));
            Ticket ticket = ticketWithId(1L);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            SlackPostMessageRequest req = postMessageCaptor.getValue();
            assertThat(req.channel()).isEqualTo(CHANNEL_ID);
            assertThat(req.threadTs()).isEqualTo(QUERY_TS);
        }

        @Test
        void slaReplyContainsRepoNamePrNumberAndTeamLabel() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text).contains(REPO);
            assertThat(text).contains("#" + PR_NUMBER);
            assertThat(text).contains(TEAM_LABEL);
        }

        @Test
        void slaReplyContainsHumanReadableDuration() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text).contains("24 hours");
        }

        @Test
        void addsConfiguredEmojiReactionToQueryMessage() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).addReaction(argThat(req ->
                    PR_EMOJI.equals(req.getName())
                            && CHANNEL_ID.equals(req.getChannel())
                            && QUERY_TS.ts().equals(req.getTimestamp())));
        }

        @Test
        void doesNotEscalateWhenSlaIsNotYetBreached() {
            // given — PR created 1 hour ago, SLA is 24 hours, not yet breached
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verifyNoInteractions(escalationProcessingService, ticketSlackService);
        }

        private void setupDetectedPr(Instant prCreatedAt) {
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, prCreatedAt, "open"));
            when(prTrackingRepository.insert(any())).thenReturn(stubTrackingRecord(prCreatedAt, prCreatedAt.plus(SLA_24H)));
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — already tracked (idempotency)
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePosted_AlreadyTracked {

        @Test
        void skipsProcessingWhenPrAlreadyTrackedForTicket() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(99L, REPO, PR_NUMBER)).thenReturn(true);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(99L));

            // then
            verify(prTrackingRepository, never()).insert(any());
            verifyNoInteractions(gitHubClient, slackClient);
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — skipped PRs (API error, already closed)
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePosted_SkippedPrs {

        @Test
        void skipsGracefullyWhenGitHubReturnsError() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenThrow(new GitHubApiException(404, "PR not found: " + REPO + "#" + PR_NUMBER));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(prTrackingRepository, never()).insert(any());
            verifyNoInteractions(slackClient);
        }

        @Test
        void postsNoticeAndClosesTicketWhenPrIsAlreadyClosed() {
            // given — PR was created long ago and is already closed/merged
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(5));
            Ticket ticket = ticketWithId(1L);
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(prTrackingProps.tags()).thenReturn(List.of("networking"));
            when(prTrackingProps.impact()).thenReturn("low");
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, prCreatedAt, "closed"));

            // when
            PrDetectionOutcome outcome = service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then — no tracking record, a notice message is posted, and the outcome signals closure
            verify(prTrackingRepository, never()).insert(any());
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text).contains("#" + PR_NUMBER).contains(REPO).contains("closed");
            assertThat(outcome.shouldCloseTicket()).isTrue();
            assertThat(outcome.closingTags()).containsExactly("networking");
            assertThat(outcome.closingImpact()).isEqualTo("low");
            verifyNoInteractions(escalationProcessingService, ticketSlackService);
        }

        @Test
        void postsNoticeAndClosesTicketForAnyNonOpenState() {
            // given — some hypothetical non-open state
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(prTrackingProps.tags()).thenReturn(List.of("networking"));
            when(prTrackingProps.impact()).thenReturn("low");
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, prCreatedAt, "merged"));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(2L));

            // then — message contains the actual state returned by GitHub
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).contains("merged");
            verify(prTrackingRepository, never()).insert(any());
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — SLA already breached at detection time
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePosted_ImmediateEscalation {

        @Test
        void escalatesImmediatelyWhenSlaAlreadyBreachedAtDetectionTime() {
            // given — PR created 2 days ago, SLA is 24 hours, already breached
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Instant slaDeadline = prCreatedAt.plus(SLA_24H);
            Ticket ticket = ticketWithId(5L);

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, prCreatedAt, "open"));
            PrTrackingRecord insertedRecord = stubTrackingRecord(prCreatedAt, slaDeadline);
            when(prTrackingRepository.insert(any())).thenReturn(insertedRecord);
            when(escalationProcessingService.createEscalation(any())).thenReturn(
                    Escalation.builder().id(new EscalationId(77L)).channelId(CHANNEL_ID).build());

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String postedText = postMessageCaptor.getValue().message().getText();
            assertThat(postedText)
                    .contains(REPO)
                    .contains("24 hours")
                    .contains("#" + PR_NUMBER)
                    .contains("exceeded that timeframe");
            verify(escalationProcessingService).createEscalation(argThat((CreateEscalationRequest req) ->
                    TEAM_CODE.equals(req.team()) && req.ticket() == ticket));
            verify(prTrackingRepository).updateStatus(
                    eq(insertedRecord.id()), eq(PrTrackingStatus.ESCALATED), eq(null), eq(77L));
            verify(ticketSlackService).markTicketEscalated(ticket.queryRef());
        }

        @Test
        void escalationIdIsNullWhenCreateEscalationReturnsNull() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, prCreatedAt, "open"));
            PrTrackingRecord insertedRecord = stubTrackingRecord(prCreatedAt, prCreatedAt.plus(SLA_24H));
            when(prTrackingRepository.insert(any())).thenReturn(insertedRecord);
            when(escalationProcessingService.createEscalation(any())).thenReturn(null);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then
            verify(prTrackingRepository).updateStatus(
                    eq(insertedRecord.id()), eq(PrTrackingStatus.ESCALATED), eq(null), eq(null));
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — multiple PRs in one message
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePosted_MultiplePrs {

        @Test
        void processesBothPrsWhenTwoLinksDetected() {
            // given
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(
                    new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H),
                    new PrTrackingRepositoryProps(repoB, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(
                    new DetectedPr(REPO, PR_NUMBER), new DetectedPr(repoB, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, createdAt, "open"));
            when(gitHubClient.getPullRequest(repoB, prB))
                    .thenReturn(new GitHubPullRequest(repoB, prB, createdAt, "open"));
            when(prTrackingRepository.insert(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then
            verify(prTrackingRepository).insert(argThat(r -> REPO.equals(r.githubRepo()) && r.prNumber() == PR_NUMBER));
            verify(prTrackingRepository).insert(argThat(r -> repoB.equals(r.githubRepo()) && r.prNumber() == prB));
            verify(slackClient, org.mockito.Mockito.times(2)).postMessage(any());
            verify(slackClient, org.mockito.Mockito.times(2)).addReaction(any());
        }

        @Test
        void skipsAlreadyTrackedButProcessesNewPrInSameMessage() {
            // given — first PR already tracked for ticket 10, second is new
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(
                    new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H),
                    new PrTrackingRepositoryProps(repoB, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(
                    new DetectedPr(REPO, PR_NUMBER), new DetectedPr(repoB, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(10L, REPO, PR_NUMBER)).thenReturn(true);
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(10L, repoB, prB)).thenReturn(false);
            when(gitHubClient.getPullRequest(repoB, prB))
                    .thenReturn(new GitHubPullRequest(repoB, prB, createdAt, "open"));
            when(prTrackingRepository.insert(any()))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — only the new PR is inserted and replied to
            verify(prTrackingRepository, org.mockito.Mockito.times(1)).insert(any());
            verify(slackClient, org.mockito.Mockito.times(1)).postMessage(any());
        }

        @Test
        void doesNotCloseWhenOnePrClosedAndOneOpen() {
            // given — two PRs: first closed, second open (ticket closes only when all are closed)
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(
                    new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H),
                    new PrTrackingRepositoryProps(repoB, TEAM_CODE, SLA_24H)));

            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(
                    new DetectedPr(REPO, PR_NUMBER), new DetectedPr(repoB, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, createdAt, "closed"));
            when(gitHubClient.getPullRequest(repoB, prB))
                    .thenReturn(new GitHubPullRequest(repoB, prB, createdAt, "open"));
            when(prTrackingRepository.insert(any()))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            PrDetectionOutcome outcome = service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — we post notice for the closed PR and track the open one; outcome is tracked (do not close)
            verify(slackClient, atLeast(1)).postMessage(argThat((SlackPostMessageRequest req) ->
                    req.message().getText().contains("closed") && req.message().getText().contains(REPO)));
            verify(prTrackingRepository).insert(argThat(r -> repoB.equals(r.githubRepo()) && r.prNumber() == prB));
            assertThat(outcome.shouldCloseTicket()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Slack reaction error handling
    // -------------------------------------------------------------------------

    @Nested
    class ReactionErrorHandling {

        @BeforeEach
        void setUpStubs() {
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));

            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, createdAt, "open"));
            when(prTrackingRepository.insert(any()))
                    .thenReturn(stubTrackingRecord(createdAt, createdAt.plus(SLA_24H)));
        }

        @Test
        void swallowsAlreadyReactedError() {
            // given
            SlackException alreadyReacted = mock(SlackException.class);
            when(alreadyReacted.getError()).thenReturn("already_reacted");
            doThrow(alreadyReacted).when(slackClient).addReaction(any());

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — no exception thrown; SLA reply was still posted
            verify(slackClient).postMessage(any());
        }

        @Test
        void swallowsOtherSlackReactionErrors() {
            // given
            SlackException otherError = mock(SlackException.class);
            when(otherError.getError()).thenReturn("channel_not_found");
            doThrow(otherError).when(slackClient).addReaction(any());

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — no exception thrown; SLA reply was still posted
            verify(slackClient).postMessage(any());
        }
    }

    // -------------------------------------------------------------------------
    // Duration formatting (via SLA reply text)
    // -------------------------------------------------------------------------

    @Nested
    class DurationFormatting {

        @Test
        void formatsOneHourAsSingular() {
            assertSlaReplyContains(Duration.ofHours(1), "1 hour");
        }

        @Test
        void formatsPluralHours() {
            assertSlaReplyContains(Duration.ofHours(36), "36 hours");
        }

        @Test
        void formatsMinutesWhenNotExactHours() {
            assertSlaReplyContains(Duration.ofMinutes(90), "90 minutes");
        }

        @Test
        void formatsSingleMinute() {
            assertSlaReplyContains(Duration.ofMinutes(1), "1 minute");
        }

        @Test
        void formatsSeconds() {
            assertSlaReplyContains(Duration.ofSeconds(200), "200 seconds");
        }

        @Test
        void formatsSingleSecond() {
            assertSlaReplyContains(Duration.ofSeconds(1), "1 second");
        }

        private void assertSlaReplyContains(Duration sla, String expectedDurationText) {
            // given
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, sla)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, createdAt, "open"));
            when(prTrackingRepository.insert(any()))
                    .thenReturn(stubTrackingRecord(createdAt, createdAt.plus(sla)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).contains(expectedDurationText);
        }
    }

    // -------------------------------------------------------------------------
    // Team label resolution
    // -------------------------------------------------------------------------

    @Nested
    class TeamLabelResolution {

        @Test
        void usesTeamLabelWhenTeamFoundInRegistry() {
            // given
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, TEAM_CODE, SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam("Infra Integration", TEAM_CODE, "SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, createdAt, "open"));
            when(prTrackingRepository.insert(any())).thenReturn(stubTrackingRecord(createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).contains("Infra Integration");
        }

        @Test
        void fallsBackToTeamCodeWhenTeamNotFoundInRegistry() {
            // given
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(
                    List.of(new PrTrackingRepositoryProps(REPO, "unknown-team", SLA_24H)));
            when(escalationTeamsRegistry.findEscalationTeamByCode("unknown-team")).thenReturn(null);
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), anyInt())).thenReturn(false);
            when(gitHubClient.getPullRequest(REPO, PR_NUMBER))
                    .thenReturn(new GitHubPullRequest(REPO, PR_NUMBER, createdAt, "open"));
            when(prTrackingRepository.insert(any())).thenReturn(stubTrackingRecord(createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).contains("unknown-team");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MessagePosted messagePostedWith(String text) {
        MessageRef ref = new MessageRef(QUERY_TS, null, CHANNEL_ID);
        return new MessagePosted(text, "U_USER", ref);
    }

    private static Ticket ticketWithId(long id) {
        return Ticket.builder()
                .id(new TicketId(id))
                .channelId(CHANNEL_ID)
                .queryTs(QUERY_TS)
                .status(TicketStatus.opened)
                .statusLog(ImmutableList.of())
                .lastInteractedAt(Instant.now())
                .build();
    }

    private static PrTrackingRecord stubTrackingRecord(Instant prCreatedAt, Instant slaDeadline) {
        return stubTrackingRecord(1L, prCreatedAt, slaDeadline);
    }

    private static PrTrackingRecord stubTrackingRecord(long id, Instant prCreatedAt, Instant slaDeadline) {
        return new PrTrackingRecord(id, 1L, REPO, PR_NUMBER, prCreatedAt, slaDeadline,
                TEAM_CODE, PrTrackingStatus.OPEN, null, null);
    }
}
