package com.coreeng.supportbot.troubleshooting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.coreeng.supportbot.troubleshooting.Payloads.DecisionKind;
import com.coreeng.supportbot.troubleshooting.Payloads.EngineerMessage;
import com.coreeng.supportbot.troubleshooting.Payloads.Proposal;
import com.coreeng.supportbot.troubleshooting.Payloads.RouterMessage;
import com.coreeng.supportbot.troubleshooting.Payloads.RouterState;
import com.coreeng.supportbot.troubleshooting.Payloads.RouterStatus;
import com.coreeng.supportbot.troubleshooting.Payloads.RunUpdate;
import com.coreeng.supportbot.troubleshooting.Payloads.SlackMessage;
import com.coreeng.supportbot.troubleshooting.router.RouterAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Runs the erasure statements from {@code docs/erasure/troubleshooting-sessions.sql}, the file the
 * runbook tells operators to run, against a real session log.
 */
@SpringJUnitConfig(TroubleshootingSessionPostgresTest.Config.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("com.coreeng.supportbot.troubleshooting.TroubleshootingSessionPostgresTest#localDatabaseEnabled")
class TroubleshootingErasurePostgresTest {
    private static final Path ERASURE_SCRIPT = Path.of("docs/erasure/troubleshooting-sessions.sql");
    private static final String TEST_CHANNEL = "C-TROUBLESHOOTING-ERASURE";
    private static final String EMAIL = "alice@example.com";
    private static final String SLACK_ID = "U0ALICE";

    private final TroubleshootingSessionService sessions;
    private final JdbcTemplate jdbc;
    private long sessionId;

    TroubleshootingErasurePostgresTest(TroubleshootingSessionService sessions, JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void createSession() {
        cleanUp();
        Long queryId = jdbc.queryForObject(
                "INSERT INTO query (ts, channel_id, date) VALUES ('2.000001', ?, now()) RETURNING id",
                Long.class,
                TEST_CHANNEL);
        Long ticketId = jdbc.queryForObject(
                "INSERT INTO ticket (query_id, status) VALUES (?, 'opened') RETURNING id", Long.class, queryId);
        sessions.createIfAbsent(ticketId == null ? 0 : ticketId);
        sessionId = sessions.requireByTicketId(ticketId == null ? 0 : ticketId).id();
    }

    @AfterEach
    void cleanUp() {
        String tickets = "SELECT t.id FROM ticket t JOIN query q ON q.id = t.query_id WHERE q.channel_id = '"
                + TEST_CHANNEL + "'";
        jdbc.update("DELETE FROM troubleshooting_event WHERE session_id IN (SELECT id FROM troubleshooting_session"
                + " WHERE ticket_id IN (" + tickets + "))");
        jdbc.update("DELETE FROM troubleshooting_session WHERE ticket_id IN (" + tickets + ")");
        jdbc.update("DELETE FROM ticket WHERE id IN (" + tickets + ")");
        jdbc.update("DELETE FROM query WHERE channel_id = ?", TEST_CHANNEL);
    }

    @Test
    void removesThePersonsEmailAndSlackIdAndKeepsTheLogReadable() throws IOException {
        slack(SLACK_ID, "My pods are pending. Reach me at Alice@Example.com");
        slack("U0BOB", "<@" + SLACK_ID + "> same here, cc " + EMAIL);
        slack("U0ALICE2", "Not the same person: <@U0ALICE2>, alice@example.org");
        sessions.append(
                sessionId, TroubleshootingEventType.ENGINEER_MESSAGE, EMAIL, new EngineerMessage("I will check quota"));
        sessions.append(
                sessionId,
                TroubleshootingEventType.ENGINEER_MESSAGE,
                "bob@example.com",
                new EngineerMessage("Thanks ALICE@example.com"));
        sessions.append(
                sessionId,
                TroubleshootingEventType.ROUTER_MESSAGE,
                Actors.ROUTER,
                new RouterMessage("Ask <@" + SLACK_ID + "> (" + EMAIL + ") for logs", "test-model", 1, 1, 1));
        sessions.append(
                sessionId,
                TroubleshootingEventType.ROUTER_STATUS,
                Actors.ROUTER,
                new RouterStatus(RouterState.FAILED, "could not reach " + EMAIL));
        TroubleshootingEvent proposal = sessions.appendRouterOutput(
                sessionId,
                TroubleshootingEventType.PROPOSAL,
                new Proposal(
                        RouterAction.REPLY,
                        "Hi <@" + SLACK_ID + ">, can you share the pod events?",
                        "Asked by " + EMAIL,
                        "test-model",
                        1,
                        1,
                        1));
        sessions.decide(sessionId, proposal.seq(), EMAIL, DecisionKind.APPROVED, "Hi, can you share the events?");
        sessions.append(
                sessionId,
                TroubleshootingEventType.RUN_UPDATE,
                Actors.SYSTEM,
                new RunUpdate("WOULD_POST", "Would post to the Slack thread (not posted):\nHi <@" + SLACK_ID + ">"));

        erase(EMAIL, SLACK_ID);

        assertThat(rowsMentioningThePerson()).isZero();
        assertThat(sessions.events(sessionId))
                .extracting(TroubleshootingEvent::seq, TroubleshootingEvent::actor, event -> event.text("text"))
                .containsExactly(
                        tuple(1L, "erased", "[erased]"),
                        tuple(2L, "slack:U0BOB", "<@erased> same here, cc [erased]"),
                        tuple(3L, "slack:U0ALICE2", "Not the same person: <@U0ALICE2>, alice@example.org"),
                        tuple(4L, "erased", "[erased]"),
                        tuple(5L, "bob@example.com", "Thanks [erased]"),
                        tuple(6L, "router", "Ask <@erased> ([erased]) for logs"),
                        tuple(7L, "router", ""),
                        tuple(8L, "router", "Hi <@erased>, can you share the pod events?"),
                        tuple(9L, "erased", "[erased]"),
                        tuple(10L, "system", "Would post to the Slack thread (not posted):\nHi <@erased>"));
        assertThat(sessions.events(sessionId).get(7).text("reason")).isEqualTo("Asked by [erased]");
        assertThat(sessions.events(sessionId).get(8).text("decision")).isEqualTo("APPROVED");
        assertThat(sessions.events(sessionId)
                        .get(8)
                        .payload()
                        .path("proposalSeq")
                        .asLong())
                .isEqualTo(8);
        assertThat(sessions.events(sessionId).get(6).text("error")).isEqualTo("could not reach [erased]");
        assertThat(sessions.events(sessionId).get(5).text("model")).isEqualTo("test-model");
        assertThat(sessions.events(sessionId)
                        .getFirst()
                        .payload()
                        .path("available")
                        .asBoolean())
                .isTrue();
        assertThat(sessions.append(
                                sessionId,
                                TroubleshootingEventType.SLACK_MESSAGE,
                                Actors.SYSTEM,
                                new SlackMessage(null, "after erasure", false))
                        .seq())
                .isEqualTo(11);
    }

    @Test
    void anIdentifierLeftEmptyMatchesNothing() throws IOException {
        slack(SLACK_ID, "Reach me at " + EMAIL);
        String before = logAsText();

        erase("", "");

        assertThat(logAsText()).isEqualTo(before);
    }

    private void slack(String userId, String text) {
        sessions.append(
                sessionId,
                TroubleshootingEventType.SLACK_MESSAGE,
                Actors.slack(userId),
                new SlackMessage("1.1", text, true));
    }

    /** Turns psql's {@code :'name'} variables into named parameters and runs each statement. */
    private void erase(String email, String slackId) throws IOException {
        String script = Files.readString(ERASURE_SCRIPT, StandardCharsets.UTF_8)
                .replace(":'email'", ":email")
                .replace(":'slack_id'", ":slack_id");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbc);
        List<String> statements = Arrays.stream(script.split(";\\s*\\n"))
                .filter(statement -> statement.lines().anyMatch(line -> !line.isBlank() && !line.startsWith("--")))
                .toList();
        assertThat(statements).isNotEmpty();
        statements.forEach(statement -> named.update(statement, Map.of("email", email, "slack_id", slackId)));
    }

    private int rowsMentioningThePerson() {
        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM troubleshooting_event e WHERE e.session_id = ?"
                        + " AND (row_to_json(e)::text ILIKE '%' || ? || '%' OR row_to_json(e)::text ~ ('\\m' || ? || '\\M'))",
                Integer.class, sessionId, EMAIL, SLACK_ID);
        Integer session = jdbc.queryForObject(
                "SELECT count(*) FROM troubleshooting_session s WHERE s.id = ?"
                        + " AND (row_to_json(s)::text ILIKE '%' || ? || '%' OR row_to_json(s)::text ~ ('\\m' || ? || '\\M'))",
                Integer.class, sessionId, EMAIL, SLACK_ID);
        return (events == null ? 0 : events) + (session == null ? 0 : session);
    }

    private String logAsText() {
        return String.join(
                "\n",
                jdbc.queryForList(
                        "SELECT row_to_json(e)::text FROM troubleshooting_event e WHERE e.session_id = ? ORDER BY seq",
                        String.class,
                        sessionId));
    }
}
