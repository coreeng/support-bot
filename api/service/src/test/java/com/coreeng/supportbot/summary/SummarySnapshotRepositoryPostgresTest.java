package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teamgroups.TeamGroup;
import com.google.common.collect.ImmutableSortedSet;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SummaryPostgresTestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class SummarySnapshotRepositoryPostgresTest {

    private static final String PROMPT_ID = "summary-prompt-test";
    private static final SummaryWindow WINDOW = new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));
    private static final SummaryScope SCOPE = SummaryScope.all(WINDOW);

    private final SummarySnapshotRepository repository;
    private final JdbcTemplate jdbcTemplate;

    SummarySnapshotRepositoryPostgresTest(SummarySnapshotRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @BeforeEach
    @AfterEach
    void clearSnapshots() {
        jdbcTemplate.update("DELETE FROM summary_snapshot WHERE prompt_id LIKE 'summary-prompt-test%'");
    }

    @Test
    void findReturnsNullWhenNothingHasBeenGenerated() {
        assertThat(repository.find(SCOPE, PROMPT_ID)).isNull();
    }

    @Test
    void upsertStoresAndReplacesInPlace() {
        repository.upsert(new SummarySnapshot(WINDOW, "", PROMPT_ID, "3@2026-03-20T10:00", "first", "model-a", null));

        SummarySnapshot stored = repository.find(SCOPE, PROMPT_ID);
        assertThat(stored).isNotNull();
        assertThat(stored.content()).isEqualTo("first");
        assertThat(stored.fingerprint()).isEqualTo("3@2026-03-20T10:00");
        assertThat(stored.model()).isEqualTo("model-a");
        assertThat(stored.generatedAt()).isNotNull();
        assertThat(stored.window()).isEqualTo(WINDOW);

        repository.upsert(new SummarySnapshot(WINDOW, "", PROMPT_ID, "4@2026-03-21T10:00", "second", "model-b", null));

        SummarySnapshot updated = repository.find(SCOPE, PROMPT_ID);
        assertThat(updated).isNotNull();
        assertThat(updated.content()).isEqualTo("second");
        assertThat(updated.fingerprint()).isEqualTo("4@2026-03-21T10:00");
        assertThat(updated.model()).isEqualTo("model-b");
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void windowsAndPromptVersionsAreCachedIndependently() {
        SummaryWindow otherWindow = new SummaryWindow(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 23));

        repository.upsert(new SummarySnapshot(WINDOW, "", PROMPT_ID, "f", "for the window", "m", null));
        repository.upsert(new SummarySnapshot(otherWindow, "", PROMPT_ID, "f", "for a day less", "m", null));
        repository.upsert(new SummarySnapshot(WINDOW, "", PROMPT_ID + "-v2", "f", "for a new prompt", "m", null));

        assertThat(repository.find(SCOPE, PROMPT_ID))
                .isNotNull()
                .extracting(SummarySnapshot::content)
                .isEqualTo("for the window");
        assertThat(repository.find(SummaryScope.all(otherWindow), PROMPT_ID))
                .isNotNull()
                .extracting(SummarySnapshot::content)
                .isEqualTo("for a day less");
        assertThat(repository.find(SCOPE, PROMPT_ID + "-v2"))
                .isNotNull()
                .extracting(SummarySnapshot::content)
                .isEqualTo("for a new prompt");
        assertThat(countRows()).isEqualTo(3);
    }

    @Test
    void aTeamGroupsSnapshotIsCachedApartFromTheWholeWindows() {
        SummaryScope group =
                new SummaryScope(WINDOW, new TeamGroup("area-a", "Area A", ImmutableSortedSet.of("team-a", "team-b")));

        repository.upsert(new SummarySnapshot(WINDOW, "", PROMPT_ID, "f", "for everyone", "m", null));
        repository.upsert(new SummarySnapshot(WINDOW, "area-a", PROMPT_ID, "g", "for the group", "m", null));
        repository.upsert(new SummarySnapshot(WINDOW, "area-a", PROMPT_ID, "g2", "for the group, again", "m", null));

        assertThat(repository.find(SCOPE, PROMPT_ID))
                .isNotNull()
                .extracting(SummarySnapshot::content)
                .isEqualTo("for everyone");
        SummarySnapshot forGroup = repository.find(group, PROMPT_ID);
        assertThat(forGroup).isNotNull();
        assertThat(forGroup.content()).isEqualTo("for the group, again");
        assertThat(forGroup.teamGroup()).isEqualTo("area-a");
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void aRowWrittenWithoutATeamGroupReadsAsTheWholeWindow() {
        // What every snapshot stored before team groups existed looks like after V41.
        jdbcTemplate.update(
                "INSERT INTO summary_snapshot (window_from, window_to, prompt_id, fingerprint, content, model)"
                        + " VALUES (?, ?, ?, 'f', 'legacy', 'm')",
                WINDOW.from(),
                WINDOW.to(),
                PROMPT_ID);

        SummarySnapshot stored = repository.find(SCOPE, PROMPT_ID);
        assertThat(stored).isNotNull();
        assertThat(stored.content()).isEqualTo("legacy");
        assertThat(stored.teamGroup()).isEqualTo(SummaryScope.ALL_KEY);
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM summary_snapshot WHERE prompt_id LIKE 'summary-prompt-test%'", Integer.class);
        return count == null ? 0 : count;
    }
}
