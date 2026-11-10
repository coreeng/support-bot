package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.coreeng.supportbot.summary.SummaryCitations.Span;
import com.google.common.collect.ImmutableList;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A citation the model got wrong costs that one citation, never the prose around it. */
class SummaryCitationsTest {

    private static final Set<Long> SHOWN = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);

    @Test
    void splitsProseIntoPlainRunsAndCitedClaims() {
        ImmutableList<Span> spans =
                SummaryCitations.parse("We need [[documentation on X|1,2]], and to fix [[problem Y|3]].");

        assertThat(spans)
                .extracting(Span::text, Span::ticketIds)
                .containsExactly(
                        tuple("We need ", ImmutableList.of()),
                        tuple("documentation on X", ImmutableList.of(1L, 2L)),
                        tuple(", and to fix ", ImmutableList.of()),
                        tuple("problem Y", ImmutableList.of(3L)),
                        tuple(".", ImmutableList.of()));
        assertThat(SummaryCitations.plainText(spans)).isEqualTo("We need documentation on X, and to fix problem Y.");
    }

    @Test
    void proseWithoutCitationsIsOnePlainRun() {
        assertThat(SummaryCitations.parse("Line one.\n\nLine two."))
                .extracting(Span::text, Span::cited)
                .containsExactly(tuple("Line one.\n\nLine two.", false));
        assertThat(SummaryCitations.parse("")).isEmpty();
    }

    @Test
    void toleratesSpacesAndHashesInTheIdList() {
        assertThat(SummaryCitations.parse("[[claim| #1 , 2]]"))
                .extracting(Span::ticketIds)
                .containsExactly(ImmutableList.of(1L, 2L));
    }

    @Test
    void aTagWithAnythingButTicketIdsReadsAsItsPhrase() {
        assertThat(SummaryCitations.parse("See [[claim|1,abc]] and [[other|]] here."))
                .extracting(Span::text, Span::cited)
                .containsExactly(tuple("See claim and other here.", false));
    }

    @Test
    void strayMarkupNeverReachesTheReader() {
        // An unclosed tag, a nested one, and a lone closing pair: each keeps its words.
        String plain =
                SummaryCitations.plainText(SummaryCitations.parse("A [[half open. Outer [[inner|1]] tail|2]] done]]."));

        assertThat(plain).isEqualTo("A half open. Outer inner tail done.");
    }

    @Test
    void strippingStrayMarkupCannotAssembleANewTag() {
        // Removing "]]" and "|1]]" in one pass would join "[" + "[x|999]" + "]" into a tag that names a
        // ticket the model was never shown.
        String sanitised = SummaryCitations.sanitise("Tenants hit []][x|999]|1]]] issues.", Set.of(1L));

        assertThat(sanitised).doesNotContain("[[").doesNotContain("]]");
        assertThat(SummaryCitations.parse(sanitised)).noneMatch(Span::cited);
    }

    @Test
    void sanitiseIsIdempotentAndOnlyEverCitesShownTickets() {
        for (String raw : new String[] {
            "Tenants hit []][x|999]|1]]] issues.",
            "A [[[claim|1]]] b [[x|2]]]] c [[[[y|3]] d",
            "[[]] [[|1]] [[a|1]] ]][[b|2]][[ [[c|999]]",
            "Nested [[outer [[inner|1]] tail|2]] and [[ok|3,4]].",
        }) {
            String once = SummaryCitations.sanitise(raw, SHOWN);

            assertThat(SummaryCitations.sanitise(once, SHOWN)).as(raw).isEqualTo(once);
            assertThat(SummaryCitations.parse(once))
                    .as(raw)
                    .flatMap(Span::ticketIds)
                    .allMatch(SHOWN::contains);
        }
    }

    @Test
    void sanitiseDropsTicketsTheModelWasNotShown() {
        assertThat(SummaryCitations.sanitise("Needs [[docs|1,99]] and [[a fix|98]].", SHOWN))
                .isEqualTo("Needs [[docs|1]] and a fix.");
    }

    @Test
    void sanitiseKeepsAtMostFiveDistinctTicketsPerClaim() {
        assertThat(SummaryCitations.sanitise("[[claim|7,1,1,2,3,4,5,6]]", SHOWN))
                .isEqualTo("[[claim|7,1,2,3,4]]");
    }

    @Test
    void sanitiseLeavesProseWithoutCitationsAlone() {
        String prose = "Plain prose, with [brackets] and a | pipe.";

        assertThat(SummaryCitations.sanitise(prose, SHOWN)).isEqualTo(prose);
    }

    @Test
    void sanitisedTextParsesBackToTheSameClaims() {
        String sanitised = SummaryCitations.sanitise("X [[one| #2 ,1]] Y [[two|3,99]] Z [[three|99]]", SHOWN);

        assertThat(SummaryCitations.parse(sanitised))
                .filteredOn(Span::cited)
                .extracting(Span::text, Span::ticketIds)
                .containsExactly(tuple("one", ImmutableList.of(2L, 1L)), tuple("two", ImmutableList.of(3L)));
    }
}
