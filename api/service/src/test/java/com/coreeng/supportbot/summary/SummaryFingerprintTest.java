package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SummaryFingerprintTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.parse("2026-03-23T10:00:00");
    private static final String ATTRIBUTION = "d41d8cd98f00b204e9800998ecf8427e";

    @Test
    void valueIsTicketsAnalysedUpdatedAtAndAttribution() {
        assertThat(new SummaryFingerprint(3, 2, UPDATED_AT, ATTRIBUTION).value())
                .isEqualTo("3/2@2026-03-23T10:00~" + ATTRIBUTION);
    }

    @Test
    void valueOfAnEmptyWindowUsesPlaceholdersForTheMissingParts() {
        assertThat(new SummaryFingerprint(0, 0, null, null).value()).isEqualTo("0/0@-~-");
    }

    @Test
    void valueCarriesTheGapsAsCountAndIdSumOnlyWhenThereAreAny() {
        SummaryFingerprint withGaps = new SummaryFingerprint(3, 2, UPDATED_AT, ATTRIBUTION, ImmutableSet.of(42L, 99L));

        assertThat(withGaps.gapCount()).isEqualTo(2);
        assertThat(withGaps.gapIdSum()).isEqualTo(141);
        assertThat(withGaps.value()).isEqualTo("3/2@2026-03-23T10:00~" + ATTRIBUTION + "#2:141");
        // The sum tells a gap swapping for another of the same count apart.
        assertThat(new SummaryFingerprint(3, 2, UPDATED_AT, ATTRIBUTION, ImmutableSet.of(42L, 100L)).value())
                .isNotEqualTo(withGaps.value());
    }

    @Test
    void attributionAloneTellsTwoFingerprintsApart() {
        SummaryFingerprint a = new SummaryFingerprint(3, 2, UPDATED_AT, "aaaa");
        SummaryFingerprint b = new SummaryFingerprint(3, 2, UPDATED_AT, "bbbb");

        assertThat(a.value()).isNotEqualTo(b.value());
    }

    @Test
    void withGapsAmongKeepsOnlyTheGivenGapsAndLeavesTheRestUntouched() {
        SummaryFingerprint current =
                new SummaryFingerprint(3, 2, UPDATED_AT, ATTRIBUTION, ImmutableSet.of(42L, 99L, 100L));

        SummaryFingerprint attempted = current.withGapsAmong(Set.of(42L, 99L, 7L));

        assertThat(attempted.gapIds()).containsExactlyInAnyOrder(42L, 99L);
        assertThat(attempted.value()).isEqualTo("3/2@2026-03-23T10:00~" + ATTRIBUTION + "#2:141");
        assertThat(current.withGapsAmong(Set.of()).value()).isEqualTo("3/2@2026-03-23T10:00~" + ATTRIBUTION);
        // Narrowing to a superset is a no-op.
        assertThat(current.withGapsAmong(Set.of(42L, 99L, 100L, 7L))).isEqualTo(current);
    }
}
