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
    void pagesListTheSuppliedAreasInTheOrderTheyFirstAppear() {
        ContextAnswer.Pages answer = new ContextAnswer.Pages(
                List.of(
                        page("deploy/a.md", "deploy", "a"),
                        page("index.md", null, "root"),
                        page("network/b.md", "network", "b"),
                        page("deploy/c.md", "deploy", "c")),
                List.of("storage"),
                null);

        assertThat(answer.areasSupplied()).containsExactly("deploy", "network");
        assertThat(answer.areasNotSupplied()).containsExactly("storage");
        assertThat(answer.question()).isNull();
    }

    @Test
    void pagesFromASourceWithoutAreasSupplyNoAreas() {
        ContextAnswer.Pages answer = StubDocumentationSource.pages(page("a.md", null, "a"), page("b.md", null, "b"));

        assertThat(answer.areasSupplied()).isEmpty();
        assertThat(answer.areasNotSupplied()).isEmpty();
    }

    @Test
    void pagesCanCarryTheDistilledQuestionAndItsTags() {
        DistilledQuestion distilled = question("How do I rotate a deploy key?", "deploy-key", "rotate-credentials");

        ContextAnswer.Pages answer = new ContextAnswer.Pages(List.of(page("a.md", null, "a")), List.of(), distilled);

        assertThat(answer.question()).isEqualTo(distilled);
        assertThat(distilled.tags()).containsExactly("deploy-key", "rotate-credentials");
    }

    @Test
    void estimatesThePageTextInTokens() {
        ContextAnswer.Pages answer =
                StubDocumentationSource.pages(page("a.md", null, "x".repeat(400)), page("b.md", null, "y".repeat(5)));

        assertThat(answer.estimatedTokens()).isEqualTo(100 + 2);
    }

    @Test
    void rejectsPagesWithoutAPage() {
        assertThatThrownBy(() -> new ContextAnswer.Pages(List.of(), List.of(), null))
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
    void rejectsAnAreaThatIsBothSuppliedAndNotSupplied() {
        assertThatThrownBy(() ->
                        new ContextAnswer.Pages(List.of(page("deploy/a.md", "deploy", "a")), List.of("deploy"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deploy");
    }

    @Test
    void copiesItsLists() {
        List<DocumentationPage> pages = new ArrayList<>(List.of(page("a.md", null, "a")));
        ContextAnswer.Pages answer = new ContextAnswer.Pages(pages, List.of(), null);

        pages.add(page("b.md", null, "b"));

        assertThat(answer.pages()).hasSize(1);
    }

    @Test
    void answersThatEndATicketCarryTheQuestion() {
        DistilledQuestion distilled = question("Is the platform down?", "outage");

        assertThat(new ContextAnswer.NotADocumentationQuestion("reports an outage", distilled).question())
                .isEqualTo(distilled);
        assertThat(new ContextAnswer.NothingCovers("no area covers billing", distilled).question())
                .isEqualTo(distilled);
    }

    @Test
    void aDistilledQuestionMustNotBeBlank() {
        assertThatThrownBy(() -> question("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFailureCarriesItsCodeAndWhetherTryingAgainCouldHelp() {
        ContextAnswer.Failed failed = new ContextAnswer.Failed("area_too_large", false);

        assertThat(failed.reasonCode()).isEqualTo("area_too_large");
        assertThat(failed.retryable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Area_Too_Large", "area too large", "1st_failure", "the model said: no"})
    void aFailureCodeIsNeverFreeText(String code) {
        assertThatThrownBy(() -> new ContextAnswer.Failed(code, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\".md", "a<b.md", "a>b.md", "a\nb.md", " "})
    void aPageIdMustBeSafeToSendAsAnAttribute(String id) {
        assertThatThrownBy(() -> page(id, null, "text")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnavailableSourceSaysWhy() {
        assertThatThrownBy(() -> new OpenResult.Unavailable(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
