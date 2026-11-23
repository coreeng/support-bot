package com.coreeng.supportbot.docanalysis;

import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.page;
import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.question;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StubDocumentationSourceTest {

    @Test
    void answersEveryThreadWithTheDefaultAnswer() {
        ContextAnswer.Pages pages = StubDocumentationSource.pages(page("a.md", null, "text"));
        StubDocumentationSource source = StubDocumentationSource.answering(pages);

        DocumentationSession session = opened(source);

        assertThat(session.findContext("[tenant] how?", 1000)).isEqualTo(pages);
        assertThat(session.findContext("[tenant] why?", 1000)).isEqualTo(pages);
    }

    @Test
    void answersAGivenThreadWithItsOwnAnswer() {
        ContextAnswer notApplicable =
                new ContextAnswer.NotADocumentationQuestion("an outage", question("Is it down?", "outage"));
        ContextAnswer nothingCovers =
                new ContextAnswer.NothingCovers("no area covers it", question("How do I pay?", "billing"));
        StubDocumentationSource source =
                StubDocumentationSource.answering(nothingCovers).answerFor("[tenant] is it down?", notApplicable);

        DocumentationSession session = opened(source);

        assertThat(session.findContext("[tenant] is it down?", 10)).isEqualTo(notApplicable);
        assertThat(session.findContext("[tenant] how do I pay?", 10)).isEqualTo(nothingCovers);
    }

    @Test
    void failsWithoutACannedAnswer() {
        DocumentationSession session = opened(new StubDocumentationSource());

        assertThat(session.findContext("[tenant] how?", 10))
                .isEqualTo(new ContextAnswer.Failed("no_canned_answer", true));
    }

    @Test
    void recordsEachRequestWithItsBudget() {
        StubDocumentationSource source = StubDocumentationSource.answering(new ContextAnswer.Failed("broken", true));
        DocumentationSession session = opened(source);

        session.findContext("[tenant] one", 100);
        session.findContext("[tenant] two", 200);

        assertThat(source.requests())
                .containsExactly(
                        new StubDocumentationSource.Request("[tenant] one", 100),
                        new StubDocumentationSource.Request("[tenant] two", 200));
        assertThat(source.openCount()).isEqualTo(1);
    }

    @Test
    void reportsItselfUnavailable() {
        StubDocumentationSource source = StubDocumentationSource.unavailable("no product areas");

        assertThat(source.open()).isEqualTo(new OpenResult.Unavailable("no product areas"));
    }

    @Test
    void aSessionKeepsTheVersionItWasOpenedWith() {
        StubDocumentationSource source = new StubDocumentationSource().version("v1");
        DocumentationSession first = opened(source);

        source.version("v2");

        assertThat(first.version()).isEqualTo("v1");
        assertThat(opened(source).version()).isEqualTo("v2");
    }

    @Test
    void hasAFingerprintThatATestCanChange() {
        StubDocumentationSource source = new StubDocumentationSource();
        assertThat(source.fingerprint()).isEqualTo(StubDocumentationSource.DEFAULT_FINGERPRINT);

        source.fingerprint("changed");

        assertThat(source.fingerprint()).isEqualTo("changed");
    }

    private static DocumentationSession opened(DocumentationSource source) {
        OpenResult result = source.open();
        assertThat(result).isInstanceOf(OpenResult.Opened.class);
        return ((OpenResult.Opened) result).session();
    }
}
