package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InFlightPrResponseTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    // Under-specified data ("quirky" triples) shouldn't occur under the insert + state-transition
    // code paths, but can arise from the pre-V15 backfill gap or a mid-update read. Per the
    // record's compact constructor comment, we accept them rather than throw — the /in-flight-prs
    // endpoint must not 500 the whole tab over one bad row. See InFlightPrResponse.java.

    @Test
    void acceptsHasSlaFalseWithNonNullSlaDeadline() {
        var response = new InFlightPrResponse(
                "org/repo",
                1,
                "url",
                "OPEN",
                "TEAM",
                NOW,
                NOW.plusSeconds(3600),
                null,
                null,
                "team",
                "Team Label",
                "C1",
                "ts1",
                null,
                false);
        assertThat(response.hasSla()).isFalse();
        assertThat(response.slaDeadline()).isNotNull();
    }

    @Test
    void acceptsHasSlaTrueWithBothSlaFieldsNull() {
        var response = new InFlightPrResponse(
                "org/repo",
                1,
                "url",
                "OPEN",
                "TEAM",
                NOW,
                null,
                null,
                null,
                "team",
                "Team Label",
                "C1",
                "ts1",
                null,
                true);
        assertThat(response.hasSla()).isTrue();
        assertThat(response.slaDeadline()).isNull();
        assertThat(response.slaRemainingSeconds()).isNull();
    }

    @Test
    void acceptsHasSlaTrueWithBothSlaFieldsNonNull() {
        var response = new InFlightPrResponse(
                "org/repo",
                1,
                "url",
                "OPEN",
                "TEAM",
                NOW,
                NOW.plusSeconds(3600),
                3600L,
                null,
                "team",
                "Team Label",
                "C1",
                "ts1",
                null,
                true);
        assertThat(response.hasSla()).isTrue();
        assertThat(response.slaDeadline()).isNotNull();
        assertThat(response.slaRemainingSeconds()).isNotNull();
    }

    @Test
    void acceptsActiveSla() {
        var response = new InFlightPrResponse(
                "org/repo",
                1,
                "url",
                "OPEN",
                "TEAM",
                NOW,
                NOW.plusSeconds(3600),
                null,
                null,
                "team",
                "Team Label",
                "C1",
                "ts1",
                null,
                true);
        assertThat(response.hasSla()).isTrue();
        assertThat(response.slaDeadline()).isNotNull();
        assertThat(response.slaRemainingSeconds()).isNull();
    }

    @Test
    void acceptsPausedSla() {
        var response = new InFlightPrResponse(
                "org/repo",
                1,
                "url",
                "CHANGES_REQUESTED",
                "TENANT",
                NOW,
                null,
                7200L,
                null,
                "team",
                "Team Label",
                "C1",
                "ts1",
                null,
                true);
        assertThat(response.hasSla()).isTrue();
        assertThat(response.slaDeadline()).isNull();
        assertThat(response.slaRemainingSeconds()).isEqualTo(7200L);
    }

    @Test
    void acceptsNoSlaPr() {
        var response = new InFlightPrResponse(
                "org/repo",
                1,
                "url",
                "OPEN",
                "TEAM",
                NOW,
                null,
                null,
                null,
                "team",
                "Team Label",
                "C1",
                "ts1",
                null,
                false);
        assertThat(response.hasSla()).isFalse();
        assertThat(response.slaDeadline()).isNull();
        assertThat(response.slaRemainingSeconds()).isNull();
    }

    @Test
    void convenienceConstructorCopiesHasSlaTrueForActiveSla() {
        var pr = new InFlightPr(
                "org/repo",
                1,
                "url",
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
        var response = new InFlightPrResponse(pr, "Team Label");
        assertThat(response.hasSla()).isTrue();
    }

    @Test
    void convenienceConstructorCopiesHasSlaTrueForPausedSla() {
        var pr = new InFlightPr(
                "org/repo",
                1,
                "url",
                "CHANGES_REQUESTED",
                "TENANT",
                NOW,
                null,
                3600L,
                null,
                "team",
                "C1",
                "ts1",
                null,
                true);
        var response = new InFlightPrResponse(pr, "Team Label");
        assertThat(response.hasSla()).isTrue();
    }

    @Test
    void convenienceConstructorCopiesHasSlaFalseForNoSlaPr() {
        var pr = new InFlightPr(
                "org/repo",
                1,
                "url",
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
        var response = new InFlightPrResponse(pr, "Team Label");
        assertThat(response.hasSla()).isFalse();
    }
}
