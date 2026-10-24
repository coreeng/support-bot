package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teamgroups.TeamGroup;
import com.google.common.collect.ImmutableSortedSet;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SummaryScopeTest {

    private static final SummaryWindow WINDOW = new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));

    @Test
    void theWholeWindowHasNoGroupKeyOrDigest() {
        SummaryScope all = SummaryScope.all(WINDOW);

        assertThat(all.isAll()).isTrue();
        assertThat(all.teamGroupKey()).isEqualTo(SummaryScope.ALL_KEY).isEmpty();
        assertThat(all.teamCodes()).isEmpty();
        assertThat(all.membershipDigest()).isNull();
        assertThat(all.describe()).isEqualTo("2026-03-10..2026-03-23");
    }

    @Test
    void aGroupIsKeyedOnItsCode() {
        SummaryScope scope = new SummaryScope(WINDOW, group("Area A", "team-a", "team-b"));

        assertThat(scope.isAll()).isFalse();
        assertThat(scope.teamGroupKey()).isEqualTo("area-a");
        assertThat(scope.teamCodes()).containsExactly("team-a", "team-b");
        assertThat(scope.describe()).isEqualTo("2026-03-10..2026-03-23 [team group area-a]");
    }

    @Test
    void theDigestIgnoresMemberOrderButNotMembershipOrLabel() {
        String digest = new SummaryScope(WINDOW, group("Area A", "team-a", "team-b")).membershipDigest();

        assertThat(digest).hasSize(16);
        assertThat(new SummaryScope(WINDOW, group("Area A", "team-b", "team-a")).membershipDigest())
                .isEqualTo(digest);
        assertThat(new SummaryScope(WINDOW, group("Area A", "team-a")).membershipDigest())
                .isNotEqualTo(digest);
        assertThat(new SummaryScope(WINDOW, group("Area B", "team-a", "team-b")).membershipDigest())
                .isNotEqualTo(digest);
    }

    private static TeamGroup group(String label, String... teamCodes) {
        return new TeamGroup("area-a", label, ImmutableSortedSet.copyOf(teamCodes));
    }
}
