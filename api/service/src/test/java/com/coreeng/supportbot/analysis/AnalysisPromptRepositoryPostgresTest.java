package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(AnalysisPromptRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class AnalysisPromptRepositoryPostgresTest {

    /**
     * SHA-256 of the prompt as it was when it still lived in api/service/analysis/prompt.md. It is the
     * prompt_id every existing analysis row was stamped with, so if the V37 seed ever stops matching
     * it, the whole analysis cache silently invalidates and every thread is re-analysed.
     */
    private static final String SEEDED_PROMPT_ID = "a306429c1eea579c033108c4c70ff859e9fc02e91fb1dffd643d1f209b16dde5";

    private final AnalysisPromptRepository repository;
    private final JdbcTemplate jdbcTemplate;

    AnalysisPromptRepositoryPostgresTest(AnalysisPromptRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    /**
     * Restores the seeded baseline without deleting it — every other test and the running app read it.
     *
     * <p>Runs after each test as well as before: this is the same database {@code make run-local} uses,
     * and Flyway will not re-seed an applied V37, so a test that left no version in use would leave the
     * local app with a broken prompt until someone fixed it by hand. Running before each test as well
     * recovers a database left dirty by a crashed run.
     */
    @BeforeEach
    @AfterEach
    void restoreSeededBaseline() {
        // Delete first: flagging version 1 while a later version is still in use trips the unique index.
        jdbcTemplate.update("DELETE FROM analysis_prompt WHERE version > 1");
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = TRUE WHERE version = 1");
    }

    @Test
    void findInUse_returnsTheSeededPromptUnchanged() {
        AnalysisPrompt prompt = repository.findInUse();

        assertThat(prompt).isNotNull();
        assertThat(prompt.version()).isEqualTo(1);
        assertThat(AnalysisService.computePromptId(prompt.content())).isEqualTo(SEEDED_PROMPT_ID);
    }

    @Test
    void findInUse_returnsTheFlaggedVersionWhenSeveralExist() {
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = FALSE WHERE version = 1");
        insertPrompt(2, "a newer draft", false);
        insertPrompt(3, "the published one", true);

        AnalysisPrompt prompt = repository.findInUse();

        assertThat(prompt).isNotNull();
        assertThat(prompt.version()).isEqualTo(3);
        assertThat(prompt.content()).isEqualTo("the published one");
    }

    @Test
    void onlyOneVersionCanBeInUse() {
        assertThatThrownBy(() -> insertPrompt(2, "a competing version", true))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findInUse_returnsNullWhenNoVersionIsFlagged() {
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = FALSE");

        assertThat(repository.findInUse()).isNull();
    }

    private void insertPrompt(int version, String content, boolean inUse) {
        jdbcTemplate.update(
                "INSERT INTO analysis_prompt (version, content, is_in_use) VALUES (?, ?, ?)", version, content, inUse);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
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
        AnalysisPromptRepository analysisPromptRepository(DSLContext dslContext) {
            return new JdbcAnalysisPromptRepository(dslContext);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
