package com.coreeng.supportbot.docanalysis;

import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.page;
import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContextAnswerTest {

    @Test
    void keepsTheOrderTheAreasWereChosenInWhateverTheOrderOfThePages() {
        ContextAnswer.Pages answer = new ContextAnswer.Pages(
                List.of(
                        page("deploy/a.md", "deploy", "a"),
                        page("index.md", null, "root"),
                        page("network/b.md", "network", "b")),
                List.of("network", "deploy"),
                List.of("storage"),
                null);

        assertThat(answer.areasConsulted()).containsExactly("network", "deploy");
    }

    @Test
    void acceptsRootPagesOutsideAnyConsultedArea() {
        ContextAnswer.Pages answer = new ContextAnswer.Pages(
                List.of(page("index.md", null, "root"), page("deploy/a.md", "deploy", "a")),
                List.of("deploy"),
                List.of(),
                null);

        assertThat(answer.pages()).hasSize(2);
    }

    @Test
    void rejectsAConsultedAreaWithoutPages() {
        assertThatThrownBy(() -> new ContextAnswer.Pages(
                        List.of(page("deploy/a.md", "deploy", "a")), List.of("deploy", "network"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("areasConsulted");
    }

    @Test
    void rejectsAPageFromAnAreaNotConsulted() {
        assertThatThrownBy(() -> new ContextAnswer.Pages(
                        List.of(page("deploy/a.md", "deploy", "a"), page("network/b.md", "network", "b")),
                        List.of("deploy"),
                        List.of(),
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("areasConsulted");
    }

    @Test
    void rejectsAnAreaConsultedTwice() {
        assertThatThrownBy(() -> new ContextAnswer.Pages(
                        List.of(page("deploy/a.md", "deploy", "a")), List.of("deploy", "deploy"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeat");
    }

    @Test
    void rejectsAnAreaThatIsBothConsultedAndNotSupplied() {
        assertThatThrownBy(() -> new ContextAnswer.Pages(
                        List.of(page("deploy/a.md", "deploy", "a")), List.of("deploy"), List.of("deploy"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }

    @Test
    void estimatesThePageTextAtThreeAndAHalfCharactersATokenRoundingUp() {
        ContextAnswer.Pages answer = StubDocumentationSource.pages(
                page("a.md", null, "x".repeat(350)), page("b.md", null, "y".repeat(8)), page("c.md", null, ""));

        assertThat(answer.estimatedTokens()).isEqualTo(100 + 3);
    }

    @Test
    void rejectsPagesWithoutAPage() {
        assertThatThrownBy(() -> new ContextAnswer.Pages(List.of(), List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NothingCovers");
    }

    @Test
    void rejectsTwoPagesWithTheSameId() {
        assertThatThrownBy(() -> StubDocumentationSource.pages(page("a.md", null, "1"), page("a.md", null, "2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.md");
    }

    @Test
    void copiesItsLists() {
        List<DocumentationPage> pages = new ArrayList<>(List.of(page("a.md", null, "a")));
        ContextAnswer.Pages answer = new ContextAnswer.Pages(pages, List.of(), List.of(), null);

        pages.add(page("b.md", null, "b"));

        assertThat(answer.pages()).hasSize(1);
    }

    @Test
    void answersThatEndATicketNeedAReason() {
        DistilledQuestion distilled = question("Is the platform down?", "outage");

        assertThatThrownBy(() -> new ContextAnswer.NotADocumentationQuestion(" ", distilled))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextAnswer.NothingCovers("", distilled))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void answersThatEndATicketNeedTheQuestion() {
        assertThatThrownBy(() -> new ContextAnswer.NotADocumentationQuestion("an outage", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContextAnswer.NothingCovers("no area covers it", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aDistilledQuestionMustNotBeBlank() {
        assertThatThrownBy(() -> question("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sharesTheCodesThatTheRunCountsAcrossSources() {
        assertThat(ContextAnswer.Failed.providerError()).isEqualTo(new ContextAnswer.Failed("provider_error", true));
        assertThat(ContextAnswer.Failed.invalidResponse())
                .isEqualTo(new ContextAnswer.Failed("invalid_response", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Area_Too_Large", "area too large", "1st_failure", "the model said: no"})
    void aFailureCodeIsNeverFreeText(String code) {
        assertThatThrownBy(() -> new ContextAnswer.Failed(code, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "a\".md",
                "a<b.md",
                "a>b.md",
                "a\nb.md",
                "a\tb.md",
                "a\u0000b.md",
                "a\u2028b.md",
                "a\u2029b.md",
                "a\u200bb.md",
                " a.md",
                "a.md ",
                " "
            })
    void aPageIdMustBeSafeToSendAsAnAttribute(String id) {
        assertThatThrownBy(() -> page(id, null, "text")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnavailableSourceSaysWhy() {
        assertThatThrownBy(() -> new OpenResult.Unavailable(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
