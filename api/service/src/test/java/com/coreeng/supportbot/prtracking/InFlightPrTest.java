package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InFlightPrTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void acceptsBothSlaFieldsSetForQuirkyReadWithoutThrowing() {
        // Design intent: this record is a read projection (populated inside the jOOQ fetch
        // lambda). A quirky row with both sla_deadline and sla_remaining_seconds populated —
        // possible under a rare mid-update read or manual DB edit — must round-trip without
        // throwing, so the /in-flight-prs endpoint does not 500 the entire tab over one row.
        // Write-path mutual exclusion is enforced by atomic single-statement UPDATEs and by
        // JdbcPrTrackingRepositoryInvariantTest, not here.
        InFlightPr pr = new InFlightPr(
                Provider.GITHUB,
                "org/repo",
                1,
                "https://github.com/org/repo/pull/1",
                "OPEN",
                "TEAM",
                NOW,
                NOW.plusSeconds(3600),
                3600L,
                null,
                "team",
                "C1",
                "ts1",
                null,
                true);
        assertThat(pr.slaDeadline()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(pr.slaRemainingSeconds()).isEqualTo(3600L);
        assertThat(pr.hasSla()).isTrue();
    }

    @Test
    void acceptsSlaDeadlineOnlyWithNullSlaRemainingSeconds() {
        InFlightPr pr = new InFlightPr(
                Provider.GITHUB,
                "org/repo",
                1,
                "https://github.com/org/repo/pull/1",
                "OPEN",
                "TEAM",
                NOW,
                NOW.plusSeconds(3600),
                null,
                null,
                "team",
                "C1",
                "ts1",
                null,
                true);
        assertThat(pr.slaDeadline()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(pr.slaRemainingSeconds()).isNull();
        assertThat(pr.hasSla()).isTrue();
    }

    @Test
    void acceptsBothNullSlaFieldsForNoSlaPr() {
        InFlightPr pr = new InFlightPr(
                Provider.GITHUB,
                "org/repo",
                1,
                "https://github.com/org/repo/pull/1",
                "OPEN",
                "TEAM",
                NOW,
                null,
                null,
                null,
                "team",
                "C1",
                "ts1",
                null,
                false);
        assertThat(pr.slaDeadline()).isNull();
        assertThat(pr.slaRemainingSeconds()).isNull();
        assertThat(pr.hasSla()).isFalse();
    }

    @Test
    void hasSlaIsReadFromComponentNotDerived() {
        // Covers the design intent: hasSla is the persisted column, not derived from SLA fields.
        // A "hasSla=false with slaDeadline set" quirk (pre-V15 backfill gap or mid-update read)
        // must round-trip without throwing — the record just carries both values through and
        // lets the frontend decide how to render.
        InFlightPr pr = new InFlightPr(
                Provider.GITHUB,
                "org/repo",
                1,
                "https://github.com/org/repo/pull/1",
                "OPEN",
                "TEAM",
                NOW,
                NOW.plusSeconds(3600),
                null,
                null,
                "team",
                "C1",
                "ts1",
                null,
                false); // intentionally disagrees with slaDeadline being set
        assertThat(pr.hasSla()).isFalse();
        assertThat(pr.slaDeadline()).isNotNull();
    }
}
