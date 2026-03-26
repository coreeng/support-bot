package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PrTrackingRecordTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(3600);

    private static final Duration SLA_REMAINING = Duration.ofHours(6);
    private static final Instant LAST_REVIEW = NOW.minusSeconds(3600);
    private static final Instant LAST_AUTHOR_ACTIVITY = NOW.minusSeconds(1800);

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullGithubRepo() {
        assertThatThrownBy(() -> new PrTrackingRecord(
                        1L,
                        1L,
                        null,
                        42,
                        NOW,
                        DEADLINE,
                        "team",
                        true,
                        PrTrackingStatus.OPEN,
                        null,
                        null,
                        SLA_REMAINING,
                        LAST_REVIEW,
                        LAST_AUTHOR_ACTIVITY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("githubRepo");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullPrCreatedAt() {
        assertThatThrownBy(() -> new PrTrackingRecord(
                        1L,
                        1L,
                        "org/repo",
                        42,
                        null,
                        DEADLINE,
                        "team",
                        true,
                        PrTrackingStatus.OPEN,
                        null,
                        null,
                        SLA_REMAINING,
                        LAST_REVIEW,
                        LAST_AUTHOR_ACTIVITY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("prCreatedAt");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullOwningTeam() {
        assertThatThrownBy(() -> new PrTrackingRecord(
                        1L,
                        1L,
                        "org/repo",
                        42,
                        NOW,
                        DEADLINE,
                        null,
                        true,
                        PrTrackingStatus.OPEN,
                        null,
                        null,
                        SLA_REMAINING,
                        LAST_REVIEW,
                        LAST_AUTHOR_ACTIVITY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("owningTeam");
    }

    @Test
    void rejectsBlankOwningTeam() {
        assertThatThrownBy(() -> new PrTrackingRecord(
                        1L,
                        1L,
                        "org/repo",
                        42,
                        NOW,
                        DEADLINE,
                        "  ",
                        true,
                        PrTrackingStatus.OPEN,
                        null,
                        null,
                        SLA_REMAINING,
                        LAST_REVIEW,
                        LAST_AUTHOR_ACTIVITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owningTeam must not be blank");
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new PrTrackingRecord(
                        1L,
                        1L,
                        "org/repo",
                        42,
                        NOW,
                        DEADLINE,
                        "team",
                        true,
                        null,
                        null,
                        null,
                        SLA_REMAINING,
                        LAST_REVIEW,
                        LAST_AUTHOR_ACTIVITY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
    }

    @Test
    void rejectsClosedStatusWithNullClosedAt() {
        assertThatThrownBy(() -> new PrTrackingRecord(
                        1L,
                        1L,
                        "org/repo",
                        42,
                        NOW,
                        null,
                        "team",
                        true,
                        PrTrackingStatus.CLOSED,
                        null,
                        null,
                        null,
                        LAST_REVIEW,
                        LAST_AUTHOR_ACTIVITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt must not be null when status is CLOSED");
    }

    @Test
    void acceptsClosedStatusWithNonNullClosedAt() {
        var record = new PrTrackingRecord(
                1L,
                1L,
                "org/repo",
                42,
                NOW,
                null,
                "team",
                true,
                PrTrackingStatus.CLOSED,
                null,
                NOW,
                null,
                LAST_REVIEW,
                LAST_AUTHOR_ACTIVITY);
        assertThat(record.status()).isEqualTo(PrTrackingStatus.CLOSED);
        assertThat(record.closedAt()).isEqualTo(NOW);
    }

    @Test
    void storesLifecycleFieldsCorrectly() {
        var record = new PrTrackingRecord(
                1L,
                1L,
                "org/repo",
                42,
                NOW,
                null,
                "team",
                true,
                PrTrackingStatus.CHANGES_REQUESTED,
                null,
                null,
                SLA_REMAINING,
                LAST_REVIEW,
                LAST_AUTHOR_ACTIVITY);

        assertThat(record.slaRemaining()).isEqualTo(SLA_REMAINING);
        assertThat(record.lastReviewAt()).isEqualTo(LAST_REVIEW);
        assertThat(record.lastAuthorActivityAt()).isEqualTo(LAST_AUTHOR_ACTIVITY);
    }
}
