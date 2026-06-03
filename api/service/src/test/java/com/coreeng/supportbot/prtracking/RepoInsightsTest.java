package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.prtracking.source.Provider;
import org.junit.jupiter.api.Test;

class RepoInsightsTest {

    @Test
    void acceptsBreachedCountWhenHasSlaIsFalse() {
        // Shouldn't occur in practice (insert pairs has_sla with sla_deadline), but degrading
        // gracefully is preferred over a 500 on the whole dashboard. See the record's compact
        // constructor for the rationale.
        var insights = new RepoInsights(Provider.GITHUB, "org/repo", "team", 5, 2, 0, 1, 0, 0, 0, false);
        assertThat(insights.hasSla()).isFalse();
        assertThat(insights.breachedCount()).isEqualTo(1);
    }

    @Test
    void acceptsZeroBreachedCountWhenHasSlaIsFalse() {
        var insights = new RepoInsights(Provider.GITHUB, "org/repo", "team", 5, 2, 0, 0, 0, 0, 0, false);
        assertThat(insights.hasSla()).isFalse();
        assertThat(insights.breachedCount()).isZero();
    }

    @Test
    void acceptsNonZeroBreachedCountWhenHasSlaIsTrue() {
        var insights = new RepoInsights(Provider.GITHUB, "org/repo", "team", 5, 2, 1, 1, 0, 0, 0, true);
        assertThat(insights.hasSla()).isTrue();
        assertThat(insights.breachedCount()).isEqualTo(1);
    }

    @Test
    void acceptsZeroBreachedCountWhenHasSlaIsTrue() {
        var insights = new RepoInsights(Provider.GITHUB, "org/repo", "team", 10, 3, 0, 0, 0, 0, 0, true);
        assertThat(insights.hasSla()).isTrue();
        assertThat(insights.breachedCount()).isZero();
    }
}
