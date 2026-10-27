package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalysisPromptRepositoryPostgresTest {

    /**
     * SHA-256 of the prompt as it was when it still lived in api/service/analysis/prompt.md. It is the
     * prompt_id the analysis rows classified before V43 are stamped with, so the V37 seed must keep
     * matching it: it names the text those rows came from.
     */
    private static final String FIRST_PROMPT_ID = "a306429c1eea579c033108c4c70ff859e9fc02e91fb1dffd643d1f209b16dde5";

    /**
     * SHA-256 of the V43 seed, the prompt in use. Any drift in that seed re-classifies every thread
     * whose analysis it stamped.
     */
    private static final String SEEDED_PROMPT_ID = "74afee4056e8b4505be52743eba2ab40e15715e19c619b569746d85429149f76";

    /** The latest classification version a migration seeds; tests reset to it. */
    private static final int SEEDED_VERSION = 2;

    /** How to recover a local database whose seeded classification prompt was deleted. */
    private static final String RESEED_HINT = "Flyway will not re-seed an applied migration: delete version 43 from"
            + " flyway_schema_history and run the migrations again. An older branch's copy of this test deletes"
            + " every classification version above 1.";

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
     * What the migrations leave in use, checked once before any reset can mask it: classification
     * version 2 (V43) and summary version 1 (V38). Every reset below re-flags version 2 itself, so
     * without this a V43 that inserted the row but never switched to it would go unnoticed.
     */
    @BeforeAll
    void migrationsLeaveTheSeededVersionsInUse() {
        List<String> inUse = jdbcTemplate.queryForList(
                "SELECT type || ' v' || version FROM analysis_prompt WHERE is_in_use ORDER BY type", String.class);
        String why = seededVersionExists()
                ? "Classification version " + SEEDED_VERSION + " exists but is not the one in use: V43 did not"
                        + " switch to it, or the flags were changed since. Fix V43, or on a local database set"
                        + " is_in_use on classification version " + SEEDED_VERSION + " only."
                : "Classification version " + SEEDED_VERSION + " is missing. " + RESEED_HINT;

        assertThat(inUse)
                .as("prompts in use after the migrations. " + why)
                .containsExactly("classification v" + SEEDED_VERSION, "summary v1");
    }

    private boolean seededVersionExists() {
        Integer seeded = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analysis_prompt WHERE type = 'classification' AND version = ?",
                Integer.class,
                SEEDED_VERSION);
        return seeded != null && seeded > 0;
    }

    /**
     * Restores the seeded baseline without deleting it — every other test and the running app read it.
     *
     * <p>Runs after each test as well as before: this is the same database {@code make run-local} uses,
     * and Flyway will not re-seed an applied migration, so a test that left no version in use, or the
     * wrong one, would leave the local app with a broken prompt until someone fixed it by hand. Running
     * before each test as well recovers a database left dirty by a crashed run.
     */
    @BeforeEach
    @AfterEach
    void restoreSeededBaseline() {
        // Scoped per type: V38 seeds a `summary` prompt alongside the `classification` one, and an
        // unscoped reset would delete or re-flag rows this test never touched.
        // Unflag before flagging: two classification versions in use at once trips the unique index.
        // Checked first, so a missing seed fails here rather than leaving no version in use.
        if (!seededVersionExists()) {
            throw new IllegalStateException(
                    "Classification prompt version " + SEEDED_VERSION + " is missing. " + RESEED_HINT);
        }
        jdbcTemplate.update(
                "DELETE FROM analysis_prompt WHERE type = 'classification' AND version > ?", SEEDED_VERSION);
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = FALSE WHERE type = 'classification'");
        jdbcTemplate.update(
                "UPDATE analysis_prompt SET is_in_use = TRUE WHERE type = 'classification' AND version = ?",
                SEEDED_VERSION);
        jdbcTemplate.update("DELETE FROM analysis_prompt WHERE type = 'summary' AND version > 1");
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = TRUE WHERE type = 'summary' AND version = 1");
    }

    @Test
    void findInUse_returnsTheSeededPromptUnchanged() {
        AnalysisPrompt prompt = repository.findInUse(AnalysisPromptType.CLASSIFICATION);

        assertThat(prompt).isNotNull();
        assertThat(prompt.version()).isEqualTo(SEEDED_VERSION);
        assertThat(AnalysisService.computePromptId(prompt.content())).isEqualTo(SEEDED_PROMPT_ID);
    }

    @Test
    void theFirstPromptIsKeptUnchanged() {
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM analysis_prompt WHERE type = 'classification' AND version = 1", String.class);

        assertThat(content).isNotNull();
        assertThat(AnalysisService.computePromptId(content)).isEqualTo(FIRST_PROMPT_ID);
    }

    @Test
    void theSeededPromptOffersTheProductBugDriver() {
        AnalysisPrompt prompt = repository.findInUse(AnalysisPromptType.CLASSIFICATION);

        assertThat(prompt).isNotNull();
        assertThat(prompt.content())
                .contains("D. Product Bug")
                .contains("Primary Driver: <one of: Knowledge Gap, Product Usability Problem, Product Temporary Issue,"
                        + " Product Bug, Feature Request, Task Request>");
    }

    @Test
    void findInUse_returnsTheFlaggedVersionWhenSeveralExist() {
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = FALSE WHERE type = 'classification'");
        insertPrompt(AnalysisPromptType.CLASSIFICATION, SEEDED_VERSION + 1, "a newer draft", false);
        insertPrompt(AnalysisPromptType.CLASSIFICATION, SEEDED_VERSION + 2, "the published one", true);

        AnalysisPrompt prompt = repository.findInUse(AnalysisPromptType.CLASSIFICATION);

        assertThat(prompt).isNotNull();
        assertThat(prompt.version()).isEqualTo(SEEDED_VERSION + 2);
        assertThat(prompt.content()).isEqualTo("the published one");
    }

    @Test
    void onlyOneVersionOfATypeCanBeInUse() {
        assertThatThrownBy(() -> insertPrompt(
                        AnalysisPromptType.CLASSIFICATION, SEEDED_VERSION + 1, "a competing version", true))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void eachTypeHasItsOwnInUseVersion() {
        // The in-use index is partial on (type), so a summary prompt being in use must not conflict
        // with the classification one — both seeds are flagged simultaneously.
        AnalysisPrompt classification = repository.findInUse(AnalysisPromptType.CLASSIFICATION);
        AnalysisPrompt summary = repository.findInUse(AnalysisPromptType.SUMMARY);

        assertThat(classification).isNotNull();
        assertThat(summary).isNotNull();
        assertThat(summary.version()).isEqualTo(1);
        assertThat(summary.content()).isNotEqualTo(classification.content());
    }

    @Test
    void versionsAreScopedPerType() {
        // Classification version 2 is seeded, so a per-type unique constraint is the only reason this
        // insert of summary version 2 can coexist with it.
        insertPrompt(AnalysisPromptType.SUMMARY, 2, "a summary draft", false);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM analysis_prompt WHERE version = 2", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void findInUse_returnsNullWhenNoVersionIsFlagged() {
        jdbcTemplate.update("UPDATE analysis_prompt SET is_in_use = FALSE");

        assertThat(repository.findInUse(AnalysisPromptType.CLASSIFICATION)).isNull();
        assertThat(repository.findInUse(AnalysisPromptType.SUMMARY)).isNull();
    }

    private void insertPrompt(AnalysisPromptType type, int version, String content, boolean inUse) {
        jdbcTemplate.update(
                "INSERT INTO analysis_prompt (type, version, content, is_in_use) VALUES (?, ?, ?, ?)",
                type.dbValue(),
                version,
                content,
                inUse);
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
