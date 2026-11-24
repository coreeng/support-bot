package com.coreeng.supportbot.docanalysis.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.UtilsConfig;
import com.coreeng.supportbot.docanalysis.DocAnalysis;
import com.coreeng.supportbot.docanalysis.DocAnalysisReportService;
import com.coreeng.supportbot.docanalysis.DocAnalysisRepository;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunService;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunService.StartResult;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.Progress;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.StopReason;
import com.coreeng.supportbot.docanalysis.DocClassification;
import com.coreeng.supportbot.docanalysis.DocClassifier;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import com.coreeng.supportbot.docanalysis.StubDocumentationSource;
import com.coreeng.supportbot.docanalysis.WindowTicket;
import com.coreeng.supportbot.security.AllowListService;
import com.coreeng.supportbot.security.AuthCodeStore;
import com.coreeng.supportbot.security.JwtAuthenticationToken;
import com.coreeng.supportbot.security.JwtGroupTeamMerger;
import com.coreeng.supportbot.security.JwtService;
import com.coreeng.supportbot.security.OAuth2AvailabilityChecker;
import com.coreeng.supportbot.security.Role;
import com.coreeng.supportbot.security.SecurityConfig;
import com.coreeng.supportbot.security.UserPrincipal;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@WebMvcTest(
        controllers = DocAnalysisController.class,
        properties = {
            "llm.provider=vertex",
            "documentation-analysis.enabled=true",
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.test-bypass.enabled=false"
        })
@Import({
    SecurityConfig.class,
    DocAnalysisReportService.class,
    DocAnalysisMapper.class,
    DocAnalysisExceptionHandler.class,
    UtilsConfig.class
})
class DocAnalysisControllerTest {

    private static final Instant NOW = Instant.parse("2026-03-26T09:15:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 3, 10);
    private static final LocalDate TO = LocalDate.of(2026, 3, 23);
    private static final String WINDOW = "/documentation-analysis?from=2026-03-10&to=2026-03-23";
    private static final String CHANNEL = "C123";
    private static final String PROMPT_ID = "prompt-current";
    private static final String KEYS_PAGE = "guides/keys.md";
    private static final SourceStatus SOURCE_STATUS =
            new SourceStatus("commit-b", Instant.parse("2026-03-26T06:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubDocumentationSource source;

    @MockitoBean
    private DocAnalysisRunService runService;

    @MockitoBean
    private DocClassifier classifier;

    @MockitoBean
    private DocAnalysisRepository repository;

    @MockitoBean
    private SlackChannelRegistry channelRegistry;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthCodeStore authCodeStore;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private SupportTeamService supportTeamService;

    @MockitoBean
    private OAuth2AvailabilityChecker oauth2AvailabilityChecker;

    @MockitoBean
    private AllowListService allowListService;

    @MockitoBean
    private JwtGroupTeamMerger jwtGroupTeamMerger;

    // Not a @MockitoBean: HandlerMappingIntrospector implements CorsConfigurationSource, so a
    // by-type mock override would replace that MVC bean and break the slice.
    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        StubDocumentationSource documentationSource() {
            return new StubDocumentationSource();
        }
    }

    private final List<WindowTicket> tickets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        source.status(SOURCE_STATUS);
        when(classifier.currentPromptId()).thenReturn(PROMPT_ID);
        when(channelRegistry.monitoredChannelIds()).thenReturn(ImmutableList.of(CHANNEL));
        when(repository.findWindow(any(), any(), any())).thenReturn(tickets);
        givenRun(StartResult.NOTHING_DUE, DocAnalysisRunState.IDLE);
    }

    @Test
    void reportsAWindowWithResultsWhileItsRunIsActive() throws Exception {
        givenTheExampleWindow();
        givenRun(StartResult.STARTED, new DocAnalysisRunState(new Progress(FROM, TO, 2, 3), null));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(content().json(EXAMPLE_RESPONSE, JsonCompareMode.STRICT));

        verify(runService).start(FROM, TO);
        verify(repository).findWindow(FROM, TO, List.of(CHANNEL));
    }

    @Test
    void defaultsToTheLastFourteenDaysEndingYesterday() throws Exception {
        mockMvc.perform(get("/documentation-analysis")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-03-12"))
                .andExpect(jsonPath("$.to").value("2026-03-25"));

        verify(runService).start(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 25));
    }

    @Test
    void countsAResultUnderAnEarlierPromptAsNotYetClassifiedAndDoesNotListIt() throws Exception {
        tickets.add(classified(1, "2026-03-11T10:00:00Z", DocClassification.CORRECT, List.of(KEYS_PAGE), "access"));
        tickets.add(withRow(2, "2026-03-12T10:00:00Z", resultUnder("older-prompt", 2)));
        tickets.add(withRow(
                3,
                "2026-03-13T10:00:00Z",
                new DocAnalysis.FailedAttempt(new TicketId(3), "older-prompt", NOW, "undetermined", 3)));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.SUPPORT_ENGINEER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.total").value(3))
                .andExpect(jsonPath("$.counts.correct").value(1))
                .andExpect(jsonPath("$.counts.unclassified").value(2))
                .andExpect(jsonPath("$.counts.failed").value(0))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].ticketId").value("1"));
    }

    @Test
    void groupsTicketsCitingTheSamePageOnceWithTheirCount() throws Exception {
        tickets.add(classified(1, "2026-03-11T10:00:00Z", DocClassification.INCORRECT, List.of(KEYS_PAGE), "access"));
        tickets.add(classified(2, "2026-03-12T10:00:00Z", DocClassification.INCORRECT, List.of(KEYS_PAGE), "access"));
        tickets.add(classified(3, "2026-03-13T10:00:00Z", DocClassification.INCORRECT, List.of(KEYS_PAGE), "access"));
        tickets.add(classified(
                4, "2026-03-14T10:00:00Z", DocClassification.CORRECT, List.of("guides/builds.md"), "builds"));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citedPages.length()").value(2))
                .andExpect(jsonPath("$.citedPages[0].page").value(KEYS_PAGE))
                .andExpect(jsonPath("$.citedPages[0].count").value(3))
                .andExpect(jsonPath("$.citedPages[0].incorrect").value(3))
                .andExpect(jsonPath("$.citedPages[0].ticketIds[0]").value("3"))
                .andExpect(jsonPath("$.citedPages[1].page").value("guides/builds.md"))
                .andExpect(jsonPath("$.citedPages[1].count").value(1));
    }

    @Test
    void reportsTheProgressOfARunAlreadyActiveForTheWindow() throws Exception {
        givenRun(StartResult.ALREADY_ACTIVE, new DocAnalysisRunState(new Progress(FROM, TO, 10, 40), null));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                                {"run": {"state": "running",
                                         "progress": {"from": "2026-03-10", "to": "2026-03-23", "done": 10, "total": 40}}}
                                """, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.run.reason").doesNotExist());
    }

    @Test
    void reportsARunForAnotherWindowAsBusyWithThatRunsWindow() throws Exception {
        LocalDate otherFrom = LocalDate.of(2026, 1, 1);
        LocalDate otherTo = LocalDate.of(2026, 1, 14);
        givenRun(StartResult.ALREADY_ACTIVE, new DocAnalysisRunState(new Progress(otherFrom, otherTo, 5, 20), null));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("busy"))
                .andExpect(jsonPath("$.run.progress.from").value("2026-01-01"))
                .andExpect(jsonPath("$.run.progress.to").value("2026-01-14"))
                .andExpect(jsonPath("$.run.progress.done").value(5));
    }

    @Test
    void reportsARunThisInstanceDoesNotHoldAsBusyWithoutProgress() throws Exception {
        givenRun(StartResult.ALREADY_ACTIVE, DocAnalysisRunState.IDLE);

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"run\":{\"state\":\"busy\"}}", JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.run.progress").doesNotExist());
    }

    @Test
    void reportsIdleWhenNothingIsDueEvenIfAnEarlierRunStoppedAtTheDailyLimit() throws Exception {
        givenRun(StartResult.NOTHING_DUE, new DocAnalysisRunState(null, new StopReason.DailyLimitReached()));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"run\":{\"state\":\"idle\"}}", JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.run.progress").doesNotExist());
    }

    @Test
    void reportsTheDailyLimitWhileTheWindowStillHasTicketsToClassify() throws Exception {
        givenRun(StartResult.DAILY_LIMIT_REACHED, new DocAnalysisRunState(null, new StopReason.DailyLimitReached()));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("daily_limit_reached"));
    }

    @Test
    void reportsTheSourceUnavailableWithItsReason() throws Exception {
        givenRun(
                StartResult.STARTED,
                new DocAnalysisRunState(
                        null, new StopReason.SourceUnavailable("The documentation could not be fetched")));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("source_unavailable"))
                .andExpect(jsonPath("$.run.reason").value("The documentation could not be fetched"));
    }

    @Test
    void reportsAProviderOutageWithItsRetryTime() throws Exception {
        givenRun(
                StartResult.HELD_OFF,
                new DocAnalysisRunState(null, new StopReason.ProviderOutage(NOW.plusSeconds(600))));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("provider_outage"))
                .andExpect(jsonPath("$.run.retryAt").value("2026-03-26T09:25:00Z"));
    }

    @Test
    void treatsAnOutageWhoseRetryTimeHasPassedAsOver() throws Exception {
        givenRun(
                StartResult.STARTED, new DocAnalysisRunState(null, new StopReason.ProviderOutage(NOW.minusSeconds(1))));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("idle"))
                .andExpect(jsonPath("$.run.retryAt").doesNotExist());
    }

    @Test
    void reportsARunThatFailed() throws Exception {
        givenRun(StartResult.STARTED, new DocAnalysisRunState(null, new StopReason.Failed()));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("failed"));
    }

    @Test
    void reportsNoPromptInUseWithEveryTicketNotYetClassified() throws Exception {
        tickets.add(classified(1, "2026-03-11T10:00:00Z", DocClassification.CORRECT, List.of(KEYS_PAGE), "access"));
        tickets.add(withRow(2, "2026-03-12T10:00:00Z", null));
        when(runService.start(any(), any()))
                .thenThrow(new AnalysisPromptLoadException(
                        "No documentation classification prompt version is marked as in use"));

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.state").value("no_prompt"))
                .andExpect(jsonPath("$.counts.total").value(2))
                .andExpect(jsonPath("$.counts.unclassified").value(2))
                .andExpect(jsonPath("$.results").isEmpty())
                .andExpect(jsonPath("$.documentation.version").value("commit-b"));
    }

    @Test
    void omitsTheDocumentationWhenTheSourceHoldsNothingYet() throws Exception {
        source.status(null);

        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentation").doesNotExist());
    }

    @Test
    void rejectsAWindowThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(get("/documentation-analysis?from=2026-03-23&to=2026-03-10")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENTATION_ANALYSIS_WINDOW_INVALID"));

        verify(runService, never()).start(any(), any());
    }

    @Test
    void acceptsAWindowOfExactlyTheMaximumWidth() throws Exception {
        mockMvc.perform(get("/documentation-analysis?from=2025-01-01&to=2025-04-02")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk());

        verify(runService).start(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 2));
    }

    @Test
    void rejectsAWindowOneDayWiderThanTheMaximumWithoutStartingARun() throws Exception {
        mockMvc.perform(get("/documentation-analysis?from=2025-01-01&to=2025-04-03")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENTATION_ANALYSIS_WINDOW_INVALID"))
                .andExpect(jsonPath("$.detail").value("The window must not exceed 92 days (about one quarter)"));

        verify(runService, never()).start(any(), any());
    }

    @Test
    void forbidsAuthenticatedUsersWithoutLeadershipOrSupportEngineerWithoutStartingARun() throws Exception {
        mockMvc.perform(get(WINDOW).with(authentication(authTokenWithRoles(Role.USER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(head(WINDOW).with(authentication(authTokenWithRoles(Role.USER))))
                .andExpect(status().isForbidden());

        verify(runService, never()).start(any(), any());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get(WINDOW)).andExpect(status().isUnauthorized());

        verify(runService, never()).start(any(), any());
    }

    private void givenRun(StartResult started, DocAnalysisRunState state) {
        when(runService.start(any(), any())).thenReturn(started);
        when(runService.state()).thenReturn(state);
    }

    private void givenTheExampleWindow() {
        tickets.add(withRow(
                1,
                "2026-03-11T10:00:00Z",
                new DocAnalysis.Result(
                        new TicketId(1),
                        PROMPT_ID,
                        DocClassification.INCORRECT,
                        "github",
                        "commit-a",
                        "How do I rotate a deploy key?",
                        "Support said only the platform team can rotate keys.",
                        List.of("deploy-key"),
                        List.of("access"),
                        List.of(KEYS_PAGE),
                        "To rotate a key, run the rotate command.",
                        "The page says tenants rotate keys; support said they cannot.",
                        "test-model",
                        Instant.parse("2026-03-24T08:00:00Z"))));
        tickets.add(classified(2, "2026-03-12T10:00:00Z", DocClassification.MISSING, List.of(), null));
        tickets.add(classified(3, "2026-03-13T10:00:00Z", DocClassification.CORRECT, List.of(KEYS_PAGE), "access"));
        tickets.add(classified(
                4, "2026-03-14T10:00:00Z", DocClassification.MISSING, List.of("guides/builds.md"), "builds"));
        tickets.add(classified(5, "2026-03-15T10:00:00Z", DocClassification.NOT_APPLICABLE, List.of(), null));
        tickets.add(withRow(
                6,
                "2026-03-16T10:00:00Z",
                new DocAnalysis.FailedAttempt(new TicketId(6), PROMPT_ID, NOW, "quote_not_found", 1)));
        tickets.add(withRow(7, "2026-03-17T10:00:00Z", resultUnder("older-prompt", 7)));
        tickets.add(withRow(8, "2026-03-18T10:00:00Z", null));
    }

    private static WindowTicket classified(
            long id, String raisedAt, DocClassification classification, List<String> docPaths, @Nullable String area) {
        boolean cites = classification == DocClassification.CORRECT || classification == DocClassification.INCORRECT;
        return withRow(
                id,
                raisedAt,
                new DocAnalysis.Result(
                        new TicketId(id),
                        PROMPT_ID,
                        classification,
                        "github",
                        "commit-b",
                        "Question " + id + "?",
                        null,
                        List.of(),
                        area == null ? List.of() : List.of(area),
                        docPaths,
                        cites ? "Quoted passage " + id + "." : null,
                        "Rationale " + id + ".",
                        "test-model",
                        Instant.parse("2026-03-25T08:00:00Z")));
    }

    private static DocAnalysis.Result resultUnder(String promptId, long id) {
        return new DocAnalysis.Result(
                new TicketId(id),
                promptId,
                DocClassification.CORRECT,
                "github",
                "commit-a",
                "Stale question?",
                null,
                List.of(),
                List.of("access"),
                List.of(KEYS_PAGE),
                "Stale passage.",
                "Stale rationale.",
                "test-model",
                Instant.parse("2026-03-20T08:00:00Z"));
    }

    private static WindowTicket withRow(long id, String raisedAt, @Nullable DocAnalysis row) {
        return new WindowTicket(new TicketId(id), CHANNEL, "171000000" + id + ".000100", Instant.parse(raisedAt), row);
    }

    private static JwtAuthenticationToken authTokenWithRoles(Role... roles) {
        var principal = new UserPrincipal(
                "user@example.com",
                "Test User",
                ImmutableList.of(new Team("Test Tenant", "test-tenant", ImmutableList.of(TeamType.TENANT))),
                ImmutableList.copyOf(roles));
        return new JwtAuthenticationToken(principal, "test-token");
    }

    private static final String EXAMPLE_RESPONSE = """
            {
              "from": "2026-03-10",
              "to": "2026-03-23",
              "counts": {
                "total": 8,
                "correct": 1,
                "missing": 2,
                "incorrect": 1,
                "notApplicable": 1,
                "unclassified": 3,
                "failed": 1
              },
              "productAreas": [
                {"area": "access", "total": 2, "correct": 1, "missing": 0, "incorrect": 1, "notApplicable": 0},
                {"area": "builds", "total": 1, "correct": 0, "missing": 1, "incorrect": 0, "notApplicable": 0},
                {"total": 2, "correct": 0, "missing": 1, "incorrect": 0, "notApplicable": 1}
              ],
              "citedPages": [
                {"page": "guides/keys.md", "count": 2, "correct": 1, "incorrect": 1, "ticketIds": ["1", "3"]}
              ],
              "missingByArea": [
                {"area": "builds", "count": 1, "ticketIds": ["4"]},
                {"count": 1, "ticketIds": ["2"]}
              ],
              "results": [
                {
                  "ticketId": "1",
                  "channelId": "C123",
                  "queryTs": "1710000001.000100",
                  "raisedAt": "2026-03-11T10:00:00Z",
                  "classification": "incorrect",
                  "question": "How do I rotate a deploy key?",
                  "answerSummary": "Support said only the platform team can rotate keys.",
                  "tags": ["deploy-key"],
                  "docPaths": ["guides/keys.md"],
                  "evidenceQuote": "To rotate a key, run the rotate command.",
                  "rationale": "The page says tenants rotate keys; support said they cannot.",
                  "productArea": "access",
                  "productAreas": ["access"],
                  "docsVersion": "commit-a",
                  "analysedAt": "2026-03-24T08:00:00Z"
                },
                {
                  "ticketId": "4",
                  "channelId": "C123",
                  "queryTs": "1710000004.000100",
                  "raisedAt": "2026-03-14T10:00:00Z",
                  "classification": "missing",
                  "question": "Question 4?",
                  "tags": [],
                  "docPaths": ["guides/builds.md"],
                  "rationale": "Rationale 4.",
                  "productArea": "builds",
                  "productAreas": ["builds"],
                  "docsVersion": "commit-b",
                  "analysedAt": "2026-03-25T08:00:00Z"
                },
                {
                  "ticketId": "2",
                  "channelId": "C123",
                  "queryTs": "1710000002.000100",
                  "raisedAt": "2026-03-12T10:00:00Z",
                  "classification": "missing",
                  "question": "Question 2?",
                  "tags": [],
                  "docPaths": [],
                  "rationale": "Rationale 2.",
                  "productAreas": [],
                  "docsVersion": "commit-b",
                  "analysedAt": "2026-03-25T08:00:00Z"
                },
                {
                  "ticketId": "3",
                  "channelId": "C123",
                  "queryTs": "1710000003.000100",
                  "raisedAt": "2026-03-13T10:00:00Z",
                  "classification": "correct",
                  "question": "Question 3?",
                  "tags": [],
                  "docPaths": ["guides/keys.md"],
                  "evidenceQuote": "Quoted passage 3.",
                  "rationale": "Rationale 3.",
                  "productArea": "access",
                  "productAreas": ["access"],
                  "docsVersion": "commit-b",
                  "analysedAt": "2026-03-25T08:00:00Z"
                },
                {
                  "ticketId": "5",
                  "channelId": "C123",
                  "queryTs": "1710000005.000100",
                  "raisedAt": "2026-03-15T10:00:00Z",
                  "classification": "not_applicable",
                  "question": "Question 5?",
                  "tags": [],
                  "docPaths": [],
                  "rationale": "Rationale 5.",
                  "productAreas": [],
                  "docsVersion": "commit-b",
                  "analysedAt": "2026-03-25T08:00:00Z"
                }
              ],
              "run": {
                "state": "running",
                "progress": {"from": "2026-03-10", "to": "2026-03-23", "done": 2, "total": 3}
              },
              "documentation": {"version": "commit-b", "refreshedAt": "2026-03-26T06:00:00Z"}
            }
            """;
}
