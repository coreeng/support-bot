package com.coreeng.supportbot.troubleshooting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Boots the whole application with the feature on, against the database the other Postgres tests
 * use. Context tests with a hand-written configuration cannot see clashes with the rest of the
 * application, such as a second {@code ExecutorService} bean making other injections ambiguous.
 */
@SpringBootTest(
        properties = {
            "spring.config.import=file:src/main/resources/application.yaml",
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.test-bypass.enabled=true",
            "troubleshooting.enabled=true",
            "llm.provider=stub",
            "llm.stub.acknowledge-synthetic-data=true"
        })
@AutoConfigureMockMvc
@ActiveProfiles({"test", "functionaltests"})
@EnabledIf("com.coreeng.supportbot.troubleshooting.TroubleshootingSessionPostgresTest#localDatabaseEnabled")
class TroubleshootingApplicationTest {
    private static final String TEST_CHANNEL = "C-TROUBLESHOOTING-APP";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private SlackClient slackClient;

    private long ticketId;

    @DynamicPropertySource
    static void localDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres"));
        registry.add("spring.datasource.username", () -> System.getProperty("supportbot.localDb.user", "postgres"));
        registry.add("spring.datasource.password", () -> System.getProperty("supportbot.localDb.password", "postgres"));
    }

    @BeforeEach
    void createTicket() {
        cleanUp();
        Long queryId = jdbc.queryForObject(
                "INSERT INTO query (ts, channel_id, date) VALUES ('3.000001', ?, now()) RETURNING id",
                Long.class,
                TEST_CHANNEL);
        Long id = jdbc.queryForObject(
                "INSERT INTO ticket (query_id, status) VALUES (?, 'opened') RETURNING id", Long.class, queryId);
        ticketId = id == null ? 0 : id;
    }

    @AfterEach
    void cleanUp() {
        String tickets = "SELECT t.id FROM ticket t JOIN query q ON q.id = t.query_id WHERE q.channel_id = '"
                + TEST_CHANNEL + "'";
        jdbc.update("DELETE FROM troubleshooting_event WHERE session_id IN (SELECT id FROM troubleshooting_session"
                + " WHERE ticket_id IN (" + tickets + "))");
        jdbc.update("DELETE FROM troubleshooting_session WHERE ticket_id IN (" + tickets + ")");
        jdbc.update("DELETE FROM ticket_log WHERE ticket_id IN (" + tickets + ")");
        jdbc.update("DELETE FROM ticket WHERE id IN (" + tickets + ")");
        jdbc.update("DELETE FROM query WHERE channel_id = ?", TEST_CHANNEL);
    }

    @Test
    void reportsTheFeatureAsEnabled() throws Exception {
        mockMvc.perform(asEngineer(get("/troubleshooting/enabled")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":true}"));
    }

    @Test
    void startsASessionAndStreamsTheSlackThreadSnapshot() throws Exception {
        Message message = new Message();
        message.setTs("3.000001");
        message.setUser("U1");
        message.setText("Pods are pending");
        ConversationsRepliesResponse thread = new ConversationsRepliesResponse();
        thread.setMessages(List.of(message));
        when(slackClient.getThreadPage(any())).thenReturn(thread);

        mockMvc.perform(asEngineer(get(sessionPath()))).andExpect(status().isNotFound());
        mockMvc.perform(asEngineer(post(sessionPath())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andExpect(jsonPath("$.mode").value("MANUAL"));

        MvcResult stream = mockMvc.perform(asEngineer(get(sessionPath() + "/stream?after=0")))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitBody(stream, "Pods are pending");
        assertThat(body).contains("Pods are pending").contains("id:1").contains("\"actor\":\"slack:U1\"");
    }

    @Test
    void refusesUsersWhoAreNotSupportEngineers() throws Exception {
        mockMvc.perform(get(sessionPath()).header("X-Test-User", "tenant@example.com"))
                .andExpect(status().isForbidden());
    }

    private String sessionPath() {
        return "/troubleshooting/ticket/" + ticketId + "/session";
    }

    private static MockHttpServletRequestBuilder asEngineer(MockHttpServletRequestBuilder request) {
        return request.header("X-Test-User", "alice@example.com").header("X-Test-Role", "support");
    }

    private static String awaitBody(MvcResult result, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String body = result.getResponse().getContentAsString();
        while (!body.contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(50);
            body = result.getResponse().getContentAsString();
        }
        return body;
    }
}
