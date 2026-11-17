package com.coreeng.supportbot.troubleshooting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.coreeng.supportbot.troubleshooting.Payloads.DecisionKind;
import com.coreeng.supportbot.troubleshooting.Payloads.EngineerMessage;
import com.coreeng.supportbot.troubleshooting.Payloads.Proposal;
import com.coreeng.supportbot.troubleshooting.router.RouterAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(TroubleshootingSessionPostgresTest.Config.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class TroubleshootingSessionPostgresTest {
    private static final String TEST_CHANNEL = "C-TROUBLESHOOTING-TEST";
    private static final Proposal PROPOSAL =
            new Proposal(RouterAction.REPLY, "We are on it", "author waiting", "test-model", 1, 1, 1);

    private final TroubleshootingSessionService sessions;
    private final JdbcTemplate jdbc;
    private long ticketId;
    private long sessionId;

    TroubleshootingSessionPostgresTest(TroubleshootingSessionService sessions, JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @BeforeEach
    void createTicketAndSession() {
        cleanUp();
        Long queryId = jdbc.queryForObject(
                "INSERT INTO query (ts, channel_id, date) VALUES ('1.000001', ?, now()) RETURNING id",
                Long.class,
                TEST_CHANNEL);
        Long id = jdbc.queryForObject(
                "INSERT INTO ticket (query_id, status) VALUES (?, 'opened') RETURNING id", Long.class, queryId);
        ticketId = id == null ? 0 : id;
        assertThat(sessions.createIfAbsent(ticketId)).isTrue();
        assertThat(sessions.createIfAbsent(ticketId)).isFalse();
        sessionId = sessions.requireByTicketId(ticketId).id();
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
    void concurrentAppendsGetGaplessSequenceNumbers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int n = i;
            futures.add(pool.submit(() -> sessions.append(
                    sessionId, TroubleshootingEventType.ENGINEER_MESSAGE, "e" + n, new EngineerMessage("m" + n))));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(sessions.events(sessionId))
                .extracting(TroubleshootingEvent::seq)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 40).boxed().toList());
    }

    @Test
    void theSecondDecisionOnAProposalIsRefused() {
        TroubleshootingEvent proposal =
                sessions.appendRouterOutput(sessionId, TroubleshootingEventType.PROPOSAL, PROPOSAL);

        sessions.decide(sessionId, proposal.seq(), "alice@example.com", DecisionKind.APPROVED, null);

        assertThatThrownBy(() ->
                        sessions.decide(sessionId, proposal.seq(), "bob@example.com", DecisionKind.REJECTED, null))
                .isInstanceOf(ProposalNotPendingException.class);
    }

    @Test
    void aNewRouterOutputReplacesTheWaitingProposal() {
        TroubleshootingEvent first =
                sessions.appendRouterOutput(sessionId, TroubleshootingEventType.PROPOSAL, PROPOSAL);

        sessions.appendRouterOutput(sessionId, TroubleshootingEventType.PROPOSAL, PROPOSAL);

        TroubleshootingEvent superseded = sessions.events(sessionId).stream()
                .filter(event -> event.type() == TroubleshootingEventType.DECISION)
                .findFirst()
                .orElseThrow();
        assertThat(superseded.text("decision")).isEqualTo("SUPERSEDED");
        assertThat(superseded.payload().path("proposalSeq").asLong()).isEqualTo(first.seq());
        assertThatThrownBy(
                        () -> sessions.decide(sessionId, first.seq(), "alice@example.com", DecisionKind.APPROVED, null))
                .isInstanceOf(ProposalNotPendingException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres"),
                    System.getProperty("supportbot.localDb.user", "postgres"),
                    System.getProperty("supportbot.localDb.password", "postgres"));
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DSLContext dslContext(DataSource dataSource) {
            return DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TroubleshootingRepository troubleshootingRepository(DSLContext dsl) {
            return new JdbcTroubleshootingRepository(dsl, new ObjectMapper());
        }

        @Bean
        TroubleshootingSessionService troubleshootingSessionService(
                TroubleshootingRepository repository, PlatformTransactionManager transactionManager) {
            return new TroubleshootingSessionService(
                    repository, mock(TroubleshootingEventBroadcaster.class), transactionManager);
        }
    }
}
