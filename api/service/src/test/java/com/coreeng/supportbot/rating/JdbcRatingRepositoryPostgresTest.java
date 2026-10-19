package com.coreeng.supportbot.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.summary.SummaryTestFixtures;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(JdbcRatingRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class JdbcRatingRepositoryPostgresTest {
    private final JdbcRatingRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final List<UUID> ratingIds = new ArrayList<>();
    private final List<Long> ticketIds = new ArrayList<>();

    JdbcRatingRepositoryPostgresTest(JdbcRatingRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @AfterEach
    void cleanUp() {
        ratingIds.forEach(id -> jdbcTemplate.update("DELETE FROM ratings WHERE id = ?", id));
        ticketIds.forEach(id -> {
            Long queryId = jdbcTemplate.queryForObject("SELECT query_id FROM ticket WHERE id = ?", Long.class, id);
            jdbcTemplate.update("DELETE FROM ticket WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM query WHERE id = ?", queryId);
        });
    }

    @Test
    void anonymousFeedbackStoresNoTicketLink() {
        UUID ratingId = insertRating(5);

        boolean saved = repository.saveFeedback(new RatingFeedback(ratingId, "Lovely", null));

        assertThat(saved).isTrue();
        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT feedback, ticket_id FROM ratings WHERE id = ?", ratingId);
        assertThat(row.get("feedback")).isEqualTo("Lovely");
        assertThat(row.get("ticket_id")).isNull();
    }

    @Test
    void namedFeedbackLinksTicket() {
        UUID ratingId = insertRating(4);
        TicketId ticketId = insertTicket();

        repository.saveFeedback(new RatingFeedback(ratingId, "Quick fix", ticketId));

        assertThat(repository.getAllFeedback())
                .filteredOn(f -> f.ratingId().equals(ratingId))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.rating()).isEqualTo(4);
                    assertThat(f.feedback()).isEqualTo("Quick fix");
                    assertThat(f.ticketId()).isEqualTo(ticketId);
                });
    }

    @Test
    void secondFeedbackForSameRatingIsRejected() {
        UUID ratingId = insertRating(2);
        repository.saveFeedback(new RatingFeedback(ratingId, "first", null));

        boolean saved = repository.saveFeedback(new RatingFeedback(ratingId, "second", insertTicket()));

        assertThat(saved).isFalse();
        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT feedback, ticket_id FROM ratings WHERE id = ?", ratingId);
        assertThat(row.get("feedback")).isEqualTo("first");
        assertThat(row.get("ticket_id")).isNull();
    }

    @Test
    void ratingsWithoutFeedbackAreNotReturned() {
        UUID ratingId = insertRating(3);

        assertThat(repository.getAllFeedback()).noneMatch(f -> f.ratingId().equals(ratingId));
    }

    private UUID insertRating(int rating) {
        UUID id = repository.insertRating(Rating.builder()
                .rating(rating)
                .submittedTs("1704067200")
                .status(TicketStatus.closed)
                .build());
        ratingIds.add(id);
        return id;
    }

    private TicketId insertTicket() {
        long id = SummaryTestFixtures.insertTicket(
                jdbcTemplate,
                "C_RATING_TEST",
                "1704067200." + UUID.randomUUID().toString().substring(0, 6),
                LocalDateTime.of(2024, 1, 1, 0, 0),
                "closed",
                null);
        ticketIds.add(id);
        return new TicketId(id);
    }

    @Configuration
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            String url = System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres");
            String username = System.getProperty("supportbot.localDb.user", "postgres");
            String password = System.getProperty("supportbot.localDb.password", "postgres");
            return new DriverManagerDataSource(url, username, password);
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
        JdbcRatingRepository jdbcRatingRepository(DSLContext dslContext) {
            return new JdbcRatingRepository(dslContext);
        }
    }
}
