package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SummaryFingerprintTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.parse("2026-03-23T10:00:00");
    private static final Instant TICKET_UPDATED_AT = Instant.parse("2026-03-23T09:30:00Z");
    private static final String BASE = "3/2@2026-03-23T10:00~2026-03-23T09:30:00Z";

    @Test
    void valueIsTicketsAnalysedAnalysisUpdatedAtAndTicketUpdatedAt() {
        assertThat(new SummaryFingerprint(3, 2, UPDATED_AT, TICKET_UPDATED_AT).value())
                .isEqualTo(BASE);
    }

    @Test
    void valueOfAnEmptyWindowUsesPlaceholdersForTheMissingParts() {
        assertThat(new SummaryFingerprint(0, 0, null, null).value()).isEqualTo("0/0@-~-");
    }

    @Test
    void valueCarriesTheGapsAsCountAndIdSumOnlyWhenThereAreAny() {
        SummaryFingerprint withGaps = new SummaryFingerprint(3, 2, UPDATED_AT, TICKET_UPDATED_AT, ids(42, 99));

        assertThat(withGaps.gapCount()).isEqualTo(2);
        assertThat(withGaps.gapIdSum()).isEqualTo(141);
        assertThat(withGaps.value()).isEqualTo(BASE + "#2:141");
        // The sum tells a gap swapping for another of the same count apart.
        assertThat(new SummaryFingerprint(3, 2, UPDATED_AT, TICKET_UPDATED_AT, ids(42, 100)).value())
                .isNotEqualTo(withGaps.value());
    }

    @Test
    void ticketUpdatedAtAloneTellsTwoFingerprintsApart() {
        SummaryFingerprint a = new SummaryFingerprint(3, 2, UPDATED_AT, TICKET_UPDATED_AT);
        SummaryFingerprint b = new SummaryFingerprint(3, 2, UPDATED_AT, TICKET_UPDATED_AT.plusSeconds(1));

        assertThat(a.value()).isNotEqualTo(b.value());
    }

    @Test
    void withGapsAmongKeepsOnlyTheGivenGapsAndLeavesTheRestUntouched() {
        SummaryFingerprint current = new SummaryFingerprint(3, 2, UPDATED_AT, TICKET_UPDATED_AT, ids(42, 99, 100));

        SummaryFingerprint attempted = current.withGapsAmong(ids(42, 99, 7));

        assertThat(attempted.gapIds()).containsExactlyInAnyOrder(new TicketId(42), new TicketId(99));
        assertThat(attempted.value()).isEqualTo(BASE + "#2:141");
        assertThat(current.withGapsAmong(Set.of()).value()).isEqualTo(BASE);
        // Narrowing to a superset is a no-op.
        assertThat(current.withGapsAmong(ids(42, 99, 100, 7))).isEqualTo(current);
    }

    private static ImmutableSet<TicketId> ids(long... ids) {
        ImmutableSet.Builder<TicketId> set = ImmutableSet.builder();
        for (long id : ids) {
            set.add(new TicketId(id));
        }
        return set.build();
    }
}
