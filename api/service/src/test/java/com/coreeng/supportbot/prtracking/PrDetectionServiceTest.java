package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.prtracking.source.PrMetadata;
import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import com.coreeng.supportbot.prtracking.source.PrSourceClients;
import com.coreeng.supportbot.prtracking.source.PrSourceException;
import com.coreeng.supportbot.prtracking.source.Provider;
import com.coreeng.supportbot.prtracking.source.RepoCoord;
import com.coreeng.supportbot.prtracking.source.Review;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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
    private static final RepoCoord COORD = RepoCoord.github(REPO);
    private static final int PR_NUMBER = 42;
    private static final String TEAM_CODE = "infra-team";
    private static final String TEAM_LABEL = "Infra Team";
    private static final String PR_EMOJI = "pr";
    private static final Duration SLA_24H = Duration.ofHours(24);
    private static final String POSTER_USER_ID = "U_USER";
    private static final String POSTER_EMAIL = "poster@example.com";

    @Mock
    private PrUrlDispatcher prUrlParser;

    @Mock
    private PrUrlResolver prUrlResolver;

    @Mock
    private PrSourceClients prSourceClients;

    @Mock
    private PrSourceClient prSourceClient;

    @Mock
    private PrTrackingRepository prTrackingRepository;

    @Mock
    private PrTrackingProps prTrackingProps;

    @Mock
    private EscalationTeamsRegistry escalationTeamsRegistry;

    @Mock
    private EscalationProcessingService escalationProcessingService;

    @Mock
    private TicketSlackService ticketSlackService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketTeamSuggestionsService ticketTeamSuggestionsService;

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private SlackClient slackClient;

    @Mock
    private SlackTicketsProps slackTicketsProps;

    @Mock
    private SlaLookup slaLookup;

    @Mock
    private PrMessageRenderer messageRenderer;

    @Captor
    private ArgumentCaptor<SlackPostMessageRequest> postMessageCaptor;

    @Captor
    private ArgumentCaptor<NewPrTracking> newTrackingCaptor;

    private PrDetectionService service;

    @BeforeEach
    void setUp() {
        lenient().when(prSourceClients.forProvider(Provider.GITHUB)).thenReturn(prSourceClient);
        lenient().when(prUrlResolver.publicUrlFor(any(), anyInt())).thenAnswer(inv -> {
            String repo = inv.getArgument(0);
            int n = inv.getArgument(1);
            return "https://github.com/" + repo + "/pull/" + n;
        });
        service = new PrDetectionService(
                prUrlParser,
                prSourceClients,
                new TeamReviewFilter(prSourceClients),
                prTrackingRepository,
                prTrackingProps,
                escalationTeamsRegistry,
                escalationProcessingService,
                ticketSlackService,
                ticketRepository,
                ticketTeamSuggestionsService,
                platformTeamsService,
                slackClient,
                slackTicketsProps,
                slaLookup,
                messageRenderer,
                prUrlResolver);
        lenient().when(slackTicketsProps.expectedInitialReaction()).thenReturn("eyes");
        lenient().when(slaLookup.getSla(any(), any(), anyInt())).thenReturn(SLA_24H);
        lenient().when(prTrackingProps.tags()).thenReturn(List.of("pr-review"));
        lenient().when(prTrackingProps.impact()).thenReturn("medium");
        lenient().when(ticketRepository.updateTicket(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(ticketTeamSuggestionsService.getTeamSuggestions(any(), any()))
                .thenReturn(new TicketTeamsSuggestion(ImmutableList.of(), ImmutableList.of()));
    }

    // -------------------------------------------------------------------------
    // containsPrLinks
    // -------------------------------------------------------------------------

    @Nested
    class ContainsPrLinks {

        @Test
        void returnsTrueWhenParserFindsLinks() {
            // given
            when(prUrlParser.parse("some message"))
                    .thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));

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
    class HandleMessagePostedNoLinks {

        @Test
        void doesNothingWhenNoLinksDetected() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of());
            MessagePosted event = messagePostedWith("nothing interesting here");
            Ticket ticket = ticketWithId(10L);

            // when
            service.handleMessagePosted(event, ticket);

            // then
            verifyNoInteractions(
                    prSourceClient, prTrackingRepository, slackClient, ticketSlackService, escalationProcessingService);
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — happy path (SLA not yet breached)
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePostedHappyPath {

        @BeforeEach
        void setUpHappyPathStubs() {
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
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
            verify(prTrackingRepository).insertIfAbsent(newTrackingCaptor.capture());
            NewPrTracking inserted = newTrackingCaptor.getValue();
            assertThat(inserted.ticketId()).isEqualTo(99L);
            assertThat(inserted.repo()).isEqualTo(REPO);
            assertThat(inserted.prNumber()).isEqualTo(PR_NUMBER);
            assertThat(inserted.prCreatedAt()).isEqualTo(prCreatedAt);
            assertThat(inserted.slaDeadline()).isEqualTo(prCreatedAt.plus(SLA_24H));
            assertThat(inserted.owningTeam()).isEqualTo(TEAM_CODE);
            assertThat(inserted.canAutoCloseTicket()).isTrue();
        }

        @Test
        void insertsTrackingRecordWithHasSlaTrueForSlaRepo() {
            // Guards against a trivial inversion in JdbcPrTrackingRepository.insertIfAbsent — the
            // has_sla column powers BOOL_OR aggregation in the insights query, so flipping the
            // polarity on insert would silently misclassify SLA-configured repos in the dashboard.
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(prTrackingRepository).insertIfAbsent(newTrackingCaptor.capture());
            assertThat(newTrackingCaptor.getValue().hasSla()).isTrue();
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
            assertThat(text).contains("1 day");
        }

        @Test
        void addsConfiguredEmojiReactionToQueryMessage() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient)
                    .addReaction(argThat(req -> PR_EMOJI.equals(req.getName())
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
            verifyNoInteractions(escalationProcessingService);
            verify(ticketSlackService).markPostTracked(any(MessageRef.class));
        }

        @Test
        void initializesTeamTagsAndImpactForTrackedPrWhenMissing() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));
            when(ticketTeamSuggestionsService.getTeamSuggestions(any(), any()))
                    .thenReturn(new TicketTeamsSuggestion(ImmutableList.of("wow"), ImmutableList.of("core")));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(ticketRepository)
                    .updateTicket(argThat(ticket -> ticket.team() != null
                            && "wow".equals(ticket.team().toCode())
                            && ticket.tags().equals(ImmutableList.of("pr-review"))
                            && "medium".equals(ticket.impact())));
        }

        @Test
        void keepsProcessingWhenTeamSuggestionResolutionThrows() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));
            when(ticketTeamSuggestionsService.getTeamSuggestions(any(), any()))
                    .thenThrow(new RuntimeException("team suggestions unavailable"));

            // when
            PrDetectionOutcome outcome = service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            assertThat(outcome.shouldCloseTicket()).isFalse();
            verify(ticketRepository)
                    .updateTicket(argThat(ticket -> ticket.team() == null
                            && ticket.tags().equals(ImmutableList.of("pr-review"))
                            && "medium".equals(ticket.impact())));
            verify(slackClient).postMessage(any());
        }

        @Test
        void doesNotOverrideExistingTeamTagsAndImpact() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));
            Ticket ticket = ticketWithId(1L).toBuilder()
                    .team(TicketTeam.fromCode("existing-team"))
                    .tags(ImmutableList.of("manual-tag"))
                    .impact("high")
                    .build();

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then
            verify(ticketRepository, never()).updateTicket(any());
        }

        @Test
        void doesNotInitializeMetadataForThreadReplyPrLinks() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedReplyWith("msg"), ticketWithId(1L));

            // then
            verify(ticketRepository, never()).updateTicket(any());
        }

        @Test
        void marksReplyDetectedPrAsNotClosingTicketOnResolve() {
            // given
            setupDetectedPr(Instant.now().minus(Duration.ofHours(1)));

            // when
            service.handleMessagePosted(messagePostedReplyWith("msg"), ticketWithId(1L));

            // then
            verify(prTrackingRepository).insertIfAbsent(newTrackingCaptor.capture());
            assertThat(newTrackingCaptor.getValue().canAutoCloseTicket()).isFalse();
        }

        private void setupDetectedPr(Instant prCreatedAt) {
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(prCreatedAt, prCreatedAt.plus(SLA_24H)));
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — already tracked (idempotency)
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePostedAlreadyTracked {

        @Test
        void skipsProcessingWhenPrAlreadyTrackedForTicket() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(99L, Provider.GITHUB, REPO, PR_NUMBER))
                    .thenReturn(true);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(99L));

            // then
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verifyNoInteractions(prSourceClient, slackClient);
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — skipped PRs (API error, already closed)
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePostedSkippedPrs {

        @Test
        void skipsGracefullyWhenGitHubReturnsError() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenThrow(new PrSourceException("PR not found: " + REPO + "#" + PR_NUMBER));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — no PR tracked, so ticket must not be marked as tracked
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void skipsWhenPrIsAlreadyClosed() {
            // given — PR was created long ago and is already closed/merged
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(5));
            Ticket ticket = ticketWithId(1L);
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.CLOSED,
                            null,
                            List.of(),
                            List.of()));

            // when
            PrDetectionOutcome outcome = service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then — closed PR is ignored at detection-time, ticket must not be marked as tracked
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            assertThat(outcome.shouldCloseTicket()).isFalse();
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verifyNoInteractions(escalationProcessingService);
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void skipsForAnyNonOpenState() {
            // given — some hypothetical non-open state
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.MERGED,
                            null,
                            List.of(),
                            List.of()));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(2L));

            // then — merged PR is ignored, ticket must not be marked as tracked
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
            verify(prTrackingRepository, never()).insertIfAbsent(any());
        }

        @Test
        void skipsWhenNoSlaResolvable() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(slaLookup.getSla(any(), eq(COORD), eq(PR_NUMBER))).thenReturn(null);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — no SLA means PR is skipped entirely
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void skipsWhenSlaLookupThrowsPrSourceException() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(slaLookup.getSla(any(), eq(COORD), eq(PR_NUMBER))).thenThrow(new PrSourceException("server error"));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then, PrSourceException from SLA lookup skips the PR
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void skipsSideEffectsWhenInsertCollidesWithConcurrentTracker() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(null);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void skipsSideEffectsForNoSlaPrWhenInsertCollidesWithConcurrentTracker() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            // No-SLA Repo
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), null)));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(null);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verifyNoInteractions(escalationProcessingService);
        }
    }

    // -------------------------------------------------------------------------
    // No-SLA repository tracking
    // -------------------------------------------------------------------------

    @Nested
    class NoSlaRepositoryTracking {

        private static final String NO_SLA_REPO = "my-org/no-sla-repo";
        private static final RepoCoord NO_SLA_COORD = RepoCoord.github(NO_SLA_REPO);
        private static final List<String> PATHS = List.of("infra/**");

        @Test
        void tracksWithNullDeadlineWhenPrMatchesPathFilter() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER)).thenReturn(List.of("infra/main.tf"));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(1L, prCreatedAt, null));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — inserted with null slaDeadline and hasSla=false (guards the has_sla column
            // against a trivial inversion on insert — see insertsTrackingRecordWithHasSlaTrueForSlaRepo).
            verify(prTrackingRepository).insertIfAbsent(newTrackingCaptor.capture());
            assertThat(newTrackingCaptor.getValue().slaDeadline()).isNull();
            assertThat(newTrackingCaptor.getValue().hasSla()).isFalse();
            assertThat(newTrackingCaptor.getValue().repo()).isEqualTo(NO_SLA_REPO);
            // Slack: pr emoji reaction + base "eyes" reaction (2 total) + tracking message (no SLA info)
            verify(slackClient, times(2)).addReaction(any());
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText())
                    .contains(
                            "PRs to %s have no automated SLAs, they are monitored by %s team. I'll still keep an eye on this one and let you know when it moves."
                                    .formatted(NO_SLA_REPO, TEAM_CODE));
            // no escalation
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void postsCustomDetectedMessageWhenConfigured() {
            // given
            String customMessage = "Docs PRs have no automated SLA. Tag #docs-team if urgent.";
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(messageRenderer.render(eq(NO_SLA_REPO), eq(MessageEvent.DETECTED), any()))
                    .thenReturn(customMessage);
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER)).thenReturn(List.of("infra/main.tf"));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(1L, prCreatedAt, null));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — custom message replaces the default no-SLA text
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).isEqualTo(customMessage);
        }

        @Test
        void postsCustomDetectedMessageIndividuallyForGroupedPrs() {
            // given — two no-SLA PRs to the same repo with a custom detected message configured;
            // the renderer override causes grouped PRs to be posted individually.
            String customMessage = "Docs PRs have no automated SLA. Tag #docs-team if urgent.";
            int prNumber2 = PR_NUMBER + 1;
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(messageRenderer.hasOverride(NO_SLA_REPO, MessageEvent.DETECTED))
                    .thenReturn(true);
            when(messageRenderer.render(eq(NO_SLA_REPO), eq(MessageEvent.DETECTED), any()))
                    .thenReturn(customMessage);
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, NO_SLA_REPO, prNumber2)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, prNumber2))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            prNumber2,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER)).thenReturn(List.of("infra/main.tf"));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, prNumber2)).thenReturn(List.of("infra/vars.tf"));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(
                            new PrTrackingRecord(
                                    1L,
                                    1L,
                                    Provider.GITHUB,
                                    NO_SLA_REPO,
                                    PR_NUMBER,
                                    prCreatedAt,
                                    null,
                                    TEAM_CODE,
                                    true,
                                    PrTrackingStatus.OPEN,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null),
                            new PrTrackingRecord(
                                    2L,
                                    1L,
                                    Provider.GITHUB,
                                    NO_SLA_REPO,
                                    prNumber2,
                                    prCreatedAt,
                                    null,
                                    TEAM_CODE,
                                    true,
                                    PrTrackingStatus.OPEN,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — each PR gets its own custom message post (2 posts, not 1 grouped)
            verify(slackClient, times(2)).postMessage(postMessageCaptor.capture());
            postMessageCaptor
                    .getAllValues()
                    .forEach(req -> assertThat(req.message().getText()).isEqualTo(customMessage));
        }

        @Test
        void groupsMultiplePrsFromSameRepoAndMentionsTeamOnce() {
            // given — two no-SLA PRs posted to the same repo (same owning team)
            int prNumber2 = PR_NUMBER + 1;
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, NO_SLA_REPO, prNumber2)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, prNumber2))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            prNumber2,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER)).thenReturn(List.of("infra/main.tf"));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, prNumber2)).thenReturn(List.of("infra/vars.tf"));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(
                            new PrTrackingRecord(
                                    1L,
                                    1L,
                                    Provider.GITHUB,
                                    NO_SLA_REPO,
                                    PR_NUMBER,
                                    prCreatedAt,
                                    null,
                                    TEAM_CODE,
                                    true,
                                    PrTrackingStatus.OPEN,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null),
                            new PrTrackingRecord(
                                    2L,
                                    1L,
                                    Provider.GITHUB,
                                    NO_SLA_REPO,
                                    prNumber2,
                                    prCreatedAt,
                                    null,
                                    TEAM_CODE,
                                    true,
                                    PrTrackingStatus.OPEN,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — a single grouped message is posted, team mentioned once (not duplicated per PR)
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text).contains("#" + PR_NUMBER);
            assertThat(text).contains("#" + prNumber2);
            assertThat(text).containsOnlyOnce(TEAM_CODE);
        }

        @Test
        void transitionsToChangesRequestedWhenPrAlreadyHasChangesRequestedReview() {
            // given — a no-SLA PR that already has a CHANGES_REQUESTED review at detection time
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(2));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.CHANGES_REQUESTED,
                                    Instant.now().minusSeconds(600)))));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER)).thenReturn(List.of("infra/main.tf"));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(1L, prCreatedAt, null));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — status flipped to CHANGES_REQUESTED (null remaining, null escalationId)
            verify(prTrackingRepository)
                    .updateStatus(eq(1L), eq(PrTrackingStatus.CHANGES_REQUESTED), eq(null), eq(null));
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).contains("changes have been requested");
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void transitionsToApprovedWhenPrAlreadyHasApprovedReview() {
            // given — a no-SLA PR that already has an APPROVED review at detection time
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(2));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.APPROVED,
                                    Instant.now().minusSeconds(600)))));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER)).thenReturn(List.of("infra/main.tf"));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(1L, prCreatedAt, null));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — status flipped to APPROVED (null remaining, null escalationId)
            verify(prTrackingRepository).updateStatus(eq(1L), eq(PrTrackingStatus.APPROVED), eq(null), eq(null));
            verify(prTrackingRepository, never()).pauseSla(anyLong(), any(), any());
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText())
                    .contains("has been approved and is ready to merge");
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void skipsWhenNoFilesMatchPathFilter() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(List.of("src/main/java/Foo.java")); // does not match "infra/**"

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — PR skipped entirely
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void skipsWhenListPullRequestFilesThrows() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(NO_SLA_REPO, TEAM_CODE, null, PATHS, null)));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, NO_SLA_REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(NO_SLA_COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(NO_SLA_REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.listChangedFiles(NO_SLA_COORD, PR_NUMBER))
                    .thenThrow(new PrSourceException("server error"));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — conservative: skip the PR when file listing fails
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — SLA already breached at detection time
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePostedReviewAwareDetection {

        @Test
        void escalatesWhenSlaBreachedAndNoReviews() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Ticket ticket = ticketWithId(5L);
            stubBreachedPrDetection(prCreatedAt, ticket);
            when(escalationProcessingService.createEscalation(any()))
                    .thenReturn(Escalation.builder()
                            .id(new EscalationId(77L))
                            .channelId(CHANNEL_ID)
                            .build());

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then
            verify(escalationProcessingService).createEscalation(any());
            verify(prTrackingRepository).updateStatus(anyLong(), eq(PrTrackingStatus.ESCALATED), eq(null), eq(77L));
        }

        @Test
        void postsDefaultEscalatedTextOnDetectionBreachWhenNoCustomMessage() {
            // given — renderer returns null (no override configured)
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Ticket ticket = ticketWithId(5L);
            stubBreachedPrDetection(prCreatedAt, ticket);
            when(escalationProcessingService.createEscalation(any()))
                    .thenReturn(Escalation.builder()
                            .id(new EscalationId(77L))
                            .channelId(CHANNEL_ID)
                            .build());

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then tenant-thread message is the default formatEscalatedText
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text).contains("expected to be reviewed within");
            assertThat(text).contains("has exceeded that timeframe");
        }

        @Test
        void postsCustomMessageOnDetectionBreachWhenConfigured() {
            // given — renderer returns the custom escalated message
            String customMessage = "Contact #pr-reviews to chase this review.";
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Ticket ticket = ticketWithId(5L);
            Instant slaDeadline = prCreatedAt.plus(SLA_24H);
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            REPO, TEAM_CODE, null, List.of(), new PrTrackingProps.Sla(null, SLA_24H, null))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(prCreatedAt, slaDeadline));
            when(escalationProcessingService.createEscalation(any()))
                    .thenReturn(Escalation.builder()
                            .id(new EscalationId(77L))
                            .channelId(CHANNEL_ID)
                            .build());
            when(messageRenderer.render(eq(REPO), eq(MessageEvent.ESCALATED), any()))
                    .thenReturn(customMessage);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then tenant-thread message is the custom override, escalation still fires as usual
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).isEqualTo(customMessage);
            verify(escalationProcessingService).createEscalation(any());
            verify(prTrackingRepository).updateStatus(anyLong(), eq(PrTrackingStatus.ESCALATED), eq(null), eq(77L));
        }

        @Test
        void doesNotEscalateWhenSlaBreachedButChangesRequested() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            stubBreachedPrDetection(prCreatedAt, ticketWithId(5L));
            // Override PR stub to include reviews
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.CHANGES_REQUESTED,
                                    Instant.now().minusSeconds(3600)))));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then — SLA paused with zero remaining (already breached), no escalation
            verify(prTrackingRepository).pauseSla(anyLong(), eq(PrTrackingStatus.CHANGES_REQUESTED), eq(Duration.ZERO));
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void setsApprovedWhenSlaBreachedButApproved() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            stubBreachedPrDetection(prCreatedAt, ticketWithId(5L));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.APPROVED,
                                    Instant.now().minusSeconds(3600)))));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then
            verify(prTrackingRepository).pauseSla(anyLong(), eq(PrTrackingStatus.APPROVED), eq(Duration.ZERO));
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void pausesSlaWhenNotBreachedButChangesRequested() {
            // given
            Instant prCreatedAt = Instant.now().minusSeconds(3600);
            stubNonBreachedPrDetection(prCreatedAt);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.CHANGES_REQUESTED,
                                    Instant.now().minusSeconds(1800)))));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then
            verify(prTrackingRepository)
                    .pauseSla(anyLong(), eq(PrTrackingStatus.CHANGES_REQUESTED), any(Duration.class));
        }

        @Test
        void pausesSlaWhenNotBreachedButApproved() {
            // given
            Instant prCreatedAt = Instant.now().minusSeconds(3600);
            stubNonBreachedPrDetection(prCreatedAt);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.APPROVED,
                                    Instant.now().minusSeconds(1800)))));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then
            verify(prTrackingRepository).pauseSla(anyLong(), eq(PrTrackingStatus.APPROVED), any(Duration.class));
        }

        @Test
        void marksTrackingEscalatedWhenCreateEscalationReturnsNull() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Ticket ticket = ticketWithId(5L);
            stubBreachedPrDetection(prCreatedAt, ticket);
            when(escalationProcessingService.createEscalation(any())).thenReturn(null);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then — record must be moved to ESCALATED to prevent infinite poller loop
            verify(prTrackingRepository).updateStatus(anyLong(), eq(PrTrackingStatus.ESCALATED), isNull(), isNull());
            verify(ticketSlackService, never()).markTicketEscalated(any());
        }

        @Test
        void stillEscalatesWhenPostingSlaBreachMessageFails() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Ticket ticket = ticketWithId(5L);
            stubBreachedPrDetection(prCreatedAt, ticket);
            when(escalationProcessingService.createEscalation(any()))
                    .thenReturn(Escalation.builder()
                            .id(new EscalationId(77L))
                            .channelId(CHANNEL_ID)
                            .build());
            doThrow(new RuntimeException("slack down")).when(slackClient).postMessage(any());

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticket);

            // then — escalation still proceeds despite Slack failure
            verify(escalationProcessingService).createEscalation(any());
            verify(prTrackingRepository).updateStatus(anyLong(), eq(PrTrackingStatus.ESCALATED), eq(null), eq(77L));
        }

        @Test
        void usesLatestReviewWhenMixed() {
            // given — older APPROVED, newer CHANGES_REQUESTED
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            stubBreachedPrDetection(prCreatedAt, ticketWithId(5L));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(
                                    new Review(
                                            "reviewer",
                                            Review.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(3600)),
                                    new Review(
                                            "reviewer",
                                            Review.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(60)))));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then — CHANGES_REQUESTED is latest
            verify(prTrackingRepository).pauseSla(anyLong(), eq(PrTrackingStatus.CHANGES_REQUESTED), eq(Duration.ZERO));
            verifyNoInteractions(escalationProcessingService);
        }

        @Test
        void filtersReviewsToOwningTeamAtDetectionTime() {
            // given — repo configured with explicit team slug
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(2));
            Instant slaDeadline = prCreatedAt.plus(SLA_24H);
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            new PrTrackingProps.Repository(REPO, TEAM_CODE, "platform-team", List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);

            // stub team resolver — "team-member" is in the owning team, "outsider" is not
            when(prSourceClient.resolveTeamMembers(COORD, "platform-team")).thenReturn(List.of("team-member"));

            // PR has two reviews: outsider APPROVED + team-member CHANGES_REQUESTED
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(
                                    new Review(
                                            "outsider",
                                            Review.ReviewState.APPROVED,
                                            Instant.now().minusSeconds(7200)),
                                    new Review(
                                            "team-member",
                                            Review.ReviewState.CHANGES_REQUESTED,
                                            Instant.now().minusSeconds(3600)))));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(prCreatedAt, slaDeadline));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(5L));

            // then — outsider's approval is ignored; team-member's CHANGES_REQUESTED wins
            verify(prTrackingRepository).pauseSla(anyLong(), eq(PrTrackingStatus.CHANGES_REQUESTED), eq(Duration.ZERO));
            verifyNoInteractions(escalationProcessingService);
        }

        private void stubBreachedPrDetection(Instant prCreatedAt, Ticket ticket) {
            Instant slaDeadline = prCreatedAt.plus(SLA_24H);
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(prCreatedAt, slaDeadline));
        }

        private void stubNonBreachedPrDetection(Instant prCreatedAt) {
            Instant slaDeadline = prCreatedAt.plus(SLA_24H);
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any())).thenReturn(stubTrackingRecord(prCreatedAt, slaDeadline));
        }
    }

    // -------------------------------------------------------------------------
    // handleMessagePosted — multiple PRs in one message
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePostedMultiplePrs {

        @Test
        void processesBothPrsWhenTwoLinksDetected() {
            // given
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H)),
                            new PrTrackingProps.Repository(repoB, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, repoB, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(repoB), prB))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(repoB),
                            prB,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then
            verify(prTrackingRepository)
                    .insertIfAbsent(argThat(r -> REPO.equals(r.repo()) && r.prNumber() == PR_NUMBER));
            verify(prTrackingRepository).insertIfAbsent(argThat(r -> repoB.equals(r.repo()) && r.prNumber() == prB));
            verify(slackClient, org.mockito.Mockito.times(2)).postMessage(any());
            verify(slackClient, org.mockito.Mockito.times(3)).addReaction(any());
        }

        @Test
        void skipsAlreadyTrackedButProcessesNewPrInSameMessage() {
            // given — first PR already tracked for ticket 10, second is new
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H)),
                            new PrTrackingProps.Repository(repoB, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, repoB, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(10L, Provider.GITHUB, REPO, PR_NUMBER))
                    .thenReturn(true);
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(10L, Provider.GITHUB, repoB, prB))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(RepoCoord.github(repoB), prB))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(repoB),
                            prB,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — only the new PR is inserted and replied to
            verify(prTrackingRepository, org.mockito.Mockito.times(1)).insertIfAbsent(any());
            verify(slackClient, org.mockito.Mockito.times(1)).postMessage(any());
        }

        @Test
        void doesNotCloseWhenOnePrClosedAndOneOpen() {
            // given — two PRs: first closed, second open (ticket closes only when all are closed)
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H)),
                            new PrTrackingProps.Repository(repoB, TEAM_CODE, null, List.of(), sla(SLA_24H))));

            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, repoB, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.CLOSED,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(repoB), prB))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(repoB),
                            prB,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            PrDetectionOutcome outcome = service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — closed PR is ignored while open PR is tracked
            verify(prTrackingRepository).insertIfAbsent(argThat(r -> repoB.equals(r.repo()) && r.prNumber() == prB));
            assertThat(outcome.shouldCloseTicket()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Message grouping — same repo
    // -------------------------------------------------------------------------

    @Nested
    class HandleMessagePostedGrouping {

        @Test
        void postsCustomDetectedMessageIndividuallyForGroupedSlaBackedPrs() {
            // given — two SLA-backed PRs to the same repo with a custom detected message; the
            // hasOverride check causes them to be posted individually rather than grouped.
            String customMessage = "I'm tracking this PR — SLA applies.";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(messageRenderer.hasOverride(REPO, MessageEvent.DETECTED)).thenReturn(true);
            when(messageRenderer.render(eq(REPO), eq(MessageEvent.DETECTED), any()))
                    .thenReturn(customMessage);
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, REPO, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(COORD, prB))
                    .thenReturn(
                            new PrMetadata(COORD, prB, createdAt, PrMetadata.PrState.OPEN, null, List.of(), List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — each PR gets its own custom message post (2 posts, not 1 grouped)
            verify(slackClient, times(2)).postMessage(postMessageCaptor.capture());
            postMessageCaptor
                    .getAllValues()
                    .forEach(req -> assertThat(req.message().getText()).isEqualTo(customMessage));
        }

        @Test
        void groupsTrackedPrsFromSameRepoIntoOneMessage() {
            // given — two PRs from the same repo, both within SLA, no reviews
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, REPO, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(COORD, prB))
                    .thenReturn(
                            new PrMetadata(COORD, prB, createdAt, PrMetadata.PrState.OPEN, null, List.of(), List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — one grouped message instead of two
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text).contains("#" + PR_NUMBER).contains("#" + prB).contains("I'm tracking PRs");
        }

        @Test
        void groupsChangesRequestedPrsFromSameRepo() {
            // given — two PRs from same repo, both with changes requested
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, REPO, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            List<Review> changesRequestedReviews = List.of(new Review(
                    "reviewer",
                    Review.ReviewState.CHANGES_REQUESTED,
                    Instant.now().minusSeconds(60)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            changesRequestedReviews));
            when(prSourceClient.fetchPullRequest(COORD, prB))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            prB,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            changesRequestedReviews));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — one grouped changes-requested message
            verify(slackClient).postMessage(postMessageCaptor.capture());
            String text = postMessageCaptor.getValue().message().getText();
            assertThat(text)
                    .contains("PRs")
                    .contains("#" + PR_NUMBER)
                    .contains("#" + prB)
                    .contains("changes have been requested");
        }

        @Test
        void postsSeparateMessagesForMixedStatesInSameRepo() {
            // given — two PRs from same repo: one tracked, one with changes requested
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, REPO, prB)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            // PR_NUMBER has no reviews (tracked), prB has changes requested
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(COORD, prB))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            prB,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(new Review(
                                    "reviewer",
                                    Review.ReviewState.CHANGES_REQUESTED,
                                    Instant.now().minusSeconds(60)))));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two PRs"), ticketWithId(10L));

            // then — two messages: one per type
            verify(slackClient, times(2)).postMessage(any());
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
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));

            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
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

        @Test
        void swallowsSystemicSlackReactionErrors() {
            // given
            SlackException authError = mock(SlackException.class);
            when(authError.getError()).thenReturn("invalid_auth");
            doThrow(authError).when(slackClient).addReaction(any());

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
            assertSlaReplyContains(Duration.ofHours(36), "1 day 12 hours");
        }

        @Test
        void formatsMinutesWhenNotExactHours() {
            assertSlaReplyContains(Duration.ofMinutes(90), "1 hour 30 minutes");
        }

        @Test
        void formatsSingleMinute() {
            assertSlaReplyContains(Duration.ofMinutes(1), "1 minute");
        }

        @Test
        void formatsSeconds() {
            assertSlaReplyContains(Duration.ofSeconds(200), "3 minutes 20 seconds");
        }

        @Test
        void formatsSingleSecond() {
            assertSlaReplyContains(Duration.ofSeconds(1), "1 second");
        }

        @Test
        void formatsPluralDays() {
            assertThat(PrDetectionService.formatDuration(Duration.ofDays(3))).isEqualTo("3 days");
        }

        @Test
        void formatsSingularDay() {
            assertThat(PrDetectionService.formatDuration(Duration.ofDays(1))).isEqualTo("1 day");
        }

        @Test
        void formatsDaysAndHours() {
            assertThat(PrDetectionService.formatDuration(Duration.ofHours(50))).isEqualTo("2 days 2 hours");
        }

        private void assertSlaReplyContains(Duration sla, String expectedDurationText) {
            // given
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(sla))));
            when(slaLookup.getSla(any(), eq(COORD), eq(PR_NUMBER))).thenReturn(sla);
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
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
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam("Infra Integration", TEAM_CODE, "slack:SG123"));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(createdAt, createdAt.plus(SLA_24H)));

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
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            new PrTrackingProps.Repository(REPO, "unknown-team", null, List.of(), sla(SLA_24H))));
            when(escalationTeamsRegistry.findEscalationTeamByCode("unknown-team"))
                    .thenReturn(null);
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(createdAt, createdAt.plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(slackClient).postMessage(postMessageCaptor.capture());
            assertThat(postMessageCaptor.getValue().message().getText()).contains("unknown-team");
        }
    }

    // -------------------------------------------------------------------------
    // handleQueryMessagePosted — lazy ticket creation
    // -------------------------------------------------------------------------

    @Nested
    class HandleQueryMessagePosted {

        @BeforeEach
        void setUpStubs() {
            lenient().when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            lenient()
                    .when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            lenient()
                    .when(escalationTeamsRegistry.findEscalationTeamByCode(TEAM_CODE))
                    .thenReturn(new EscalationTeam(TEAM_LABEL, TEAM_CODE, "slack:SG123"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void tracksOpenPrAndCreatesTicketViaSupplier() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(prCreatedAt, prCreatedAt.plus(SLA_24H)));

            Supplier<Ticket> supplier = mock(Supplier.class);
            when(supplier.get()).thenReturn(ticketWithId(100L));

            // when
            PrDetectionOutcome outcome = service.handleQueryMessagePosted(messagePostedWith("msg"), supplier);

            // then
            verify(supplier).get();
            verify(prTrackingRepository).insertIfAbsent(any());
            verify(slackClient).postMessage(any());
            assertThat(outcome.shouldCloseTicket()).isFalse();
        }

        @Test
        @SuppressWarnings("unchecked")
        void skipsTicketCreationWhenPosterExcluded() {
            // given — the Slack poster is in an excluded team. The admission gate runs before the lazy
            // ticket creation, so no ticket (and no in-thread ticket form) is created for an untracked PR.
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            Instant.now().minus(Duration.ofHours(1)),
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(),
                            "anyone"));
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            REPO,
                            TEAM_CODE,
                            Provider.GITHUB,
                            null,
                            null,
                            List.of(),
                            sla(SLA_24H),
                            null,
                            null,
                            List.of("platform-team"),
                            false,
                            null,
                            false)));
            stubSlackPosterInTeams("platform-team");

            Supplier<Ticket> supplier = mock(Supplier.class);

            // when
            service.handleQueryMessagePosted(messagePostedWith("msg"), supplier);

            // then — admission precedes ticket creation: nothing created, posted, or tracked
            verify(supplier, never()).get();
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void createsTicketAndTracksWhenPosterNotExcluded() {
            // given — poster is in no excluded team: the query path behaves exactly as without the gate,
            // creating the ticket via the supplier and tracking the PR.
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(),
                            "anyone"));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(new PrTrackingProps.Repository(
                            REPO,
                            TEAM_CODE,
                            Provider.GITHUB,
                            null,
                            null,
                            List.of(),
                            sla(SLA_24H),
                            null,
                            null,
                            List.of("platform-team"),
                            false,
                            null,
                            false)));
            stubSlackPosterInTeams("some-other-team");
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(prCreatedAt, prCreatedAt.plus(SLA_24H)));

            Supplier<Ticket> supplier = mock(Supplier.class);
            when(supplier.get()).thenReturn(ticketWithId(100L));

            // when
            service.handleQueryMessagePosted(messagePostedWith("msg"), supplier);

            // then — ticket created once, PR tracked
            verify(supplier).get();
            verify(prTrackingRepository).insertIfAbsent(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void doesNotCreateTicketWhenAllPrsClosed() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofDays(5));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.MERGED,
                            null,
                            List.of(),
                            List.of()));

            Supplier<Ticket> supplier = mock(Supplier.class);

            // when
            service.handleQueryMessagePosted(messagePostedWith("msg"), supplier);

            // then
            verify(supplier, never()).get();
            verify(prTrackingRepository, never()).insertIfAbsent(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void doesNotCreateTicketWhenPrFetchFails() {
            // given
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenThrow(new PrSourceException("PR not found: " + REPO + "#" + PR_NUMBER));

            Supplier<Ticket> supplier = mock(Supplier.class);

            // when
            service.handleQueryMessagePosted(messagePostedWith("msg"), supplier);

            // then
            verify(supplier, never()).get();
            verify(prTrackingRepository, never()).insertIfAbsent(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void skipsAlreadyTrackedPr() {
            // given
            Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            prCreatedAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(100L, Provider.GITHUB, REPO, PR_NUMBER))
                    .thenReturn(true);

            Supplier<Ticket> supplier = mock(Supplier.class);
            when(supplier.get()).thenReturn(ticketWithId(100L));

            // when
            service.handleQueryMessagePosted(messagePostedWith("msg"), supplier);

            // then
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).postMessage(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void supplierCalledOnlyOnce() {
            // given — two open PRs, supplier should be called exactly once
            String repoB = "my-org/other-repo";
            int prB = 99;
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));

            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H)),
                            new PrTrackingProps.Repository(repoB, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, repoB, prB)));
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prSourceClient.fetchPullRequest(RepoCoord.github(repoB), prB))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(repoB),
                            prB,
                            createdAt,
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of()));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(1L, createdAt, createdAt.plus(SLA_24H)))
                    .thenReturn(stubTrackingRecord(2L, createdAt, createdAt.plus(SLA_24H)));

            Supplier<Ticket> supplier = mock(Supplier.class);
            when(supplier.get()).thenReturn(ticketWithId(100L));

            // when
            service.handleQueryMessagePosted(messagePostedWith("two PRs"), supplier);

            // then
            verify(supplier, times(1)).get();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Author admission gate (#285)
    // -------------------------------------------------------------------------

    @Nested
    class AuthorAdmissionGate {

        private static final String EXCLUDED_TEAM = "platform-team";
        private static final String GITLAB_REPO = "my-group/my-project";
        private static final RepoCoord GITLAB_COORD = RepoCoord.gitlab(GITLAB_REPO);
        private static final String NO_SLA_REPO = "my-org/no-sla-repo";
        private static final RepoCoord NO_SLA_COORD = RepoCoord.github(NO_SLA_REPO);

        private PrTrackingProps.Repository repoWithExcludeAuthorTeams(String... teams) {
            return new PrTrackingProps.Repository(
                    REPO,
                    TEAM_CODE,
                    Provider.GITHUB,
                    null,
                    null,
                    List.of(),
                    sla(SLA_24H),
                    null,
                    null,
                    List.of(teams),
                    false,
                    null,
                    false);
        }

        private void stubDetectedOpenPr(@Nullable String authorLogin) {
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            RepoCoord.github(REPO),
                            PR_NUMBER,
                            Instant.now().minus(Duration.ofHours(1)),
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(),
                            authorLogin));
        }

        private void stubDetectedOpenPr(Provider provider, String repo, RepoCoord coord, @Nullable String authorLogin) {
            when(prUrlParser.parse(any())).thenReturn(List.of(new DetectedPr(provider, repo, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(coord, PR_NUMBER))
                    .thenReturn(new PrMetadata(
                            coord,
                            PR_NUMBER,
                            Instant.now().minus(Duration.ofHours(1)),
                            PrMetadata.PrState.OPEN,
                            null,
                            List.of(),
                            List.of(),
                            authorLogin));
        }

        private PrTrackingProps.Repository gitlabRepoWithExcludeAuthorTeams(String... teams) {
            return new PrTrackingProps.Repository(
                    GITLAB_REPO,
                    TEAM_CODE,
                    Provider.GITLAB,
                    null,
                    null,
                    List.of(),
                    sla(SLA_24H),
                    null,
                    null,
                    List.of(teams),
                    false,
                    null,
                    false);
        }

        private PrTrackingProps.Repository noSlaRepoWithExcludeAuthorTeams(String... teams) {
            return new PrTrackingProps.Repository(
                    NO_SLA_REPO,
                    TEAM_CODE,
                    Provider.GITHUB,
                    null,
                    null,
                    List.of("infra/**"),
                    null,
                    null,
                    null,
                    List.of(teams),
                    false,
                    null,
                    false);
        }

        @Test
        void skipsTrackingWhenPosterInExcludedTeam() {
            // given — the Slack poster belongs to the configured excluded team
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            stubSlackPosterInTeams(EXCLUDED_TEAM);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — no record, no Slack side effects
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void tracksWhenPosterNotInAnyExcludedTeam() {
            // given — poster's teams are disjoint from the excluded team
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            stubSlackPosterInTeams("some-other-team");
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — tracked, and the poster's membership was genuinely resolved (disjoint teams)
            verify(prTrackingRepository).insertIfAbsent(any());
            verify(slackClient).getUserById(SlackId.user(POSTER_USER_ID));
            verify(platformTeamsService).listTeamsByUserEmail(POSTER_EMAIL);
        }

        @Test
        void skipsWhenPosterIsInAnyOfMultipleExcludedTeams() {
            // given — poster is in the second configured excluded team (any-of semantics)
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams("team-a", "team-b")));
            stubSlackPosterInTeams("team-b");

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void tracksWhenNoExcludeListConfigured() {
            // given — no exclude-author-teams: every PR is tracked, gate never touches Slack
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(
                            List.of(new PrTrackingProps.Repository(REPO, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — tracked, and the poster was never resolved
            verify(prTrackingRepository).insertIfAbsent(any());
            verify(slackClient, never()).getUserById(any());
            verify(platformTeamsService, never()).listTeamsByUserEmail(any());
        }

        @Test
        void tracksWhenPosterLookupThrowsFailsOpen() {
            // given — resolving the poster's Slack profile fails; must not drop the PR
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            when(slackClient.getUserById(SlackId.user(POSTER_USER_ID))).thenThrow(new RuntimeException("slack down"));
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(prTrackingRepository).insertIfAbsent(any());
        }

        @Test
        void tracksWhenPosterHasNoEmailFailsOpen() {
            // given — the poster's Slack profile carries no email, so membership can't be determined
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            User noEmailUser = new User();
            noEmailUser.setProfile(new User.Profile());
            when(slackClient.getUserById(SlackId.user(POSTER_USER_ID))).thenReturn(noEmailUser);
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — fail open, and we never looked up teams for a blank email
            verify(prTrackingRepository).insertIfAbsent(any());
            verify(platformTeamsService, never()).listTeamsByUserEmail(any());
        }

        @Test
        void skipsTrackingForGitlabRepoWhenPosterExcluded() {
            // given — gate is provider-agnostic; the GitLab MR is dropped on the same poster check
            when(prSourceClients.forProvider(Provider.GITLAB)).thenReturn(prSourceClient);
            stubDetectedOpenPr(Provider.GITLAB, GITLAB_REPO, GITLAB_COORD, "anyone");
            when(prTrackingProps.repositories()).thenReturn(List.of(gitlabRepoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            stubSlackPosterInTeams(EXCLUDED_TEAM);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
        }

        @Test
        void skipsForNoSlaRepoWhenPosterExcludedBeforePathFilter() {
            // given — gate runs before the no-SLA path filter, so it must short-circuit there too
            stubDetectedOpenPr(Provider.GITHUB, NO_SLA_REPO, NO_SLA_COORD, "anyone");
            when(prTrackingProps.repositories()).thenReturn(List.of(noSlaRepoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            stubSlackPosterInTeams(EXCLUDED_TEAM);

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — skipped before any record, Slack side effect, or path lookup
            verify(prTrackingRepository, never()).insertIfAbsent(any());
            verify(slackClient, never()).addReaction(any());
            verify(slackClient, never()).postMessage(any());
            verify(ticketSlackService, never()).markPostTracked(any());
            verify(prSourceClient, never()).listChangedFiles(any(), anyInt());
        }

        @Test
        void tracksWhenPosterResolvesToNoTeams() {
            // given — the poster resolves successfully but belongs to no platform team. This is the
            // Optional.of(emptySet()) branch — distinct from an unresolvable poster (Optional.empty()):
            // a resolved member of no team is in no excluded team, so the PR is tracked.
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            stubSlackPosterInTeams(); // resolves with an email, but zero teams
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — tracked, and membership was genuinely resolved (not the fail-open path)
            verify(prTrackingRepository).insertIfAbsent(any());
            verify(platformTeamsService).listTeamsByUserEmail(POSTER_EMAIL);
        }

        @Test
        void resolvesPosterOncePerMessageWithMultiplePrLinks() {
            // given — two PR links from the same poster in one message. resolvePosterTeamCodes is
            // hoisted out of the per-PR loop, so the poster is resolved once regardless of link count.
            int secondPr = PR_NUMBER + 1;
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, REPO, secondPr)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER)).thenReturn(openGithubPr(REPO, PR_NUMBER));
            when(prSourceClient.fetchPullRequest(COORD, secondPr)).thenReturn(openGithubPr(REPO, secondPr));
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            stubSlackPosterInTeams(EXCLUDED_TEAM);

            // when
            service.handleMessagePosted(messagePostedWith("two prs"), ticketWithId(1L));

            // then — both links dropped on the same resolved membership; resolved exactly once
            verify(slackClient, times(1)).getUserById(any());
            verify(platformTeamsService, times(1)).listTeamsByUserEmail(any());
            verify(prTrackingRepository, never()).insertIfAbsent(any());
        }

        @Test
        void skipsExcludedRepoButTracksRepoWithoutExcludeListInSameMessage() {
            // given — one message links a PR in a repo WITH exclude-author-teams and a PR in a repo
            // WITHOUT one. The poster is excluded, but authorExcluded short-circuits per-repo on an
            // empty deny-list, so only the deny-listed repo's PR is dropped; the other is tracked.
            String openRepo = "my-org/open-repo";
            RepoCoord openCoord = RepoCoord.github(openRepo);
            when(prUrlParser.parse(any()))
                    .thenReturn(List.of(
                            new DetectedPr(Provider.GITHUB, REPO, PR_NUMBER),
                            new DetectedPr(Provider.GITHUB, openRepo, PR_NUMBER)));
            when(prTrackingRepository.existsByTicketIdAndRepoAndPrNumber(anyLong(), any(), any(), anyInt()))
                    .thenReturn(false);
            when(prSourceClient.fetchPullRequest(COORD, PR_NUMBER)).thenReturn(openGithubPr(REPO, PR_NUMBER));
            when(prSourceClient.fetchPullRequest(openCoord, PR_NUMBER)).thenReturn(openGithubPr(openRepo, PR_NUMBER));
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories())
                    .thenReturn(List.of(
                            repoWithExcludeAuthorTeams(EXCLUDED_TEAM),
                            new PrTrackingProps.Repository(openRepo, TEAM_CODE, null, List.of(), sla(SLA_24H))));
            stubSlackPosterInTeams(EXCLUDED_TEAM);
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("two prs"), ticketWithId(1L));

            // then — exactly the non-excluded repo's PR is tracked; the poster is resolved once
            verify(prTrackingRepository, times(1)).insertIfAbsent(any());
            verify(slackClient, times(1)).getUserById(any());
        }

        @Test
        void tracksWhenPosterEmailBlankFailsOpen() {
            // given — the poster's Slack email is blank (whitespace); treated like no email (fail open),
            // and we never attempt a team lookup for a blank email.
            stubDetectedOpenPr("anyone");
            when(prTrackingProps.prEmoji()).thenReturn(PR_EMOJI);
            when(prTrackingProps.repositories()).thenReturn(List.of(repoWithExcludeAuthorTeams(EXCLUDED_TEAM)));
            User blankEmailUser = new User();
            User.Profile blankProfile = new User.Profile();
            blankProfile.setEmail("   ");
            blankEmailUser.setProfile(blankProfile);
            when(slackClient.getUserById(SlackId.user(POSTER_USER_ID))).thenReturn(blankEmailUser);
            when(prTrackingRepository.insertIfAbsent(any()))
                    .thenReturn(stubTrackingRecord(Instant.now(), Instant.now().plus(SLA_24H)));

            // when
            service.handleMessagePosted(messagePostedWith("msg"), ticketWithId(1L));

            // then — fail open, and we never looked up teams for a blank email
            verify(prTrackingRepository).insertIfAbsent(any());
            verify(platformTeamsService, never()).listTeamsByUserEmail(any());
        }

        private PrMetadata openGithubPr(String repo, int number) {
            return new PrMetadata(
                    RepoCoord.github(repo),
                    number,
                    Instant.now().minus(Duration.ofHours(1)),
                    PrMetadata.PrState.OPEN,
                    null,
                    List.of(),
                    List.of(),
                    "anyone");
        }
    }

    private static MessagePosted messagePostedWith(String text) {
        MessageRef ref = new MessageRef(QUERY_TS, null, CHANNEL_ID);
        return new MessagePosted(text, POSTER_USER_ID, ref);
    }

    /** Stubs the Slack poster (the message author) resolving to the given platform-team codes. */
    private void stubSlackPosterInTeams(String... teamCodes) {
        User user = new User();
        User.Profile profile = new User.Profile();
        profile.setEmail(POSTER_EMAIL);
        user.setProfile(profile);
        when(slackClient.getUserById(SlackId.user(POSTER_USER_ID))).thenReturn(user);
        ImmutableList.Builder<PlatformTeam> teams = ImmutableList.builder();
        for (String code : teamCodes) {
            teams.add(new PlatformTeam(code, code, Set.of(), Set.of()));
        }
        when(platformTeamsService.listTeamsByUserEmail(POSTER_EMAIL)).thenReturn(teams.build());
    }

    private static MessagePosted messagePostedReplyWith(String text) {
        MessageRef ref = new MessageRef(MessageTs.of("1700000001.000001"), QUERY_TS, CHANNEL_ID);
        return new MessagePosted(text, POSTER_USER_ID, ref);
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

    private static PrTrackingProps.Sla sla(Duration defaultSla) {
        return new PrTrackingProps.Sla(null, defaultSla, null);
    }

    private static PrTrackingRecord stubTrackingRecord(Instant prCreatedAt, @Nullable Instant slaDeadline) {
        return stubTrackingRecord(1L, prCreatedAt, slaDeadline);
    }

    private static PrTrackingRecord stubTrackingRecord(long id, Instant prCreatedAt, @Nullable Instant slaDeadline) {
        return new PrTrackingRecord(
                id,
                1L,
                Provider.GITHUB,
                REPO,
                PR_NUMBER,
                prCreatedAt,
                slaDeadline,
                TEAM_CODE,
                true,
                PrTrackingStatus.OPEN,
                null,
                null,
                null,
                null,
                null);
    }
}
