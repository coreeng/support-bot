package com.coreeng.supportbot.docanalysis;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** A source's answer to {@link DocumentationSession#findContext}. */
public sealed interface ContextAnswer {

    /**
     * The pages to classify the thread against, in the order they are to be sent.
     *
     * @param areasNotSupplied product areas that exist and whose pages are not among {@code pages};
     *     empty for a source without areas
     * @param question null when the source did not distil one; the classification call then does
     */
    record Pages(
            List<DocumentationPage> pages,
            List<String> areasNotSupplied,
            @Nullable DistilledQuestion question) implements ContextAnswer {

        public Pages {
            pages = List.copyOf(pages);
            areasNotSupplied = List.copyOf(areasNotSupplied);
            if (pages.isEmpty()) {
                throw new IllegalArgumentException("pages must not be empty; answer NothingCovers instead");
            }
            Set<String> ids = new HashSet<>();
            for (DocumentationPage page : pages) {
                if (!ids.add(page.id())) {
                    throw new IllegalArgumentException("page ids must be unique: " + page.id());
                }
            }
            Set<String> supplied = areasOf(pages);
            for (String area : areasNotSupplied) {
                if (area.isBlank()) {
                    throw new IllegalArgumentException("areasNotSupplied must not hold a blank name");
                }
                if (supplied.contains(area)) {
                    throw new IllegalArgumentException("area is both supplied and not supplied: " + area);
                }
            }
        }

        /** The product areas of the pages, in the order they first appear. */
        public List<String> areasSupplied() {
            return List.copyOf(areasOf(pages));
        }

        public int estimatedTokens() {
            return pages.stream()
                    .mapToInt(page -> TokenEstimator.estimate(page.text()))
                    .sum();
        }

        private static Set<String> areasOf(List<DocumentationPage> pages) {
            Set<String> areas = new LinkedHashSet<>();
            pages.stream().map(DocumentationPage::area).filter(Objects::nonNull).forEach(areas::add);
            return areas;
        }
    }

    /**
     * The thread asks a documentation question that nothing in the documentation covers. Nothing
     * later distils the question, so it is required.
     */
    record NothingCovers(String reason, DistilledQuestion question) implements ContextAnswer {}

    /** The thread asks no documentation question. Nothing later distils the question, so it is required. */
    record NotADocumentationQuestion(String reason, DistilledQuestion question) implements ContextAnswer {}

    /**
     * @param reasonCode stored as the result's failure reason, so it is a fixed code, never model or
     *     provider text
     * @param retryable false when trying again cannot help until the prompts or settings change
     */
    record Failed(String reasonCode, boolean retryable) implements ContextAnswer {

        private static final Pattern REASON_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        public Failed {
            if (!REASON_CODE.matcher(reasonCode).matches()) {
                throw new IllegalArgumentException(
                        "reasonCode must be lowercase letters, digits and underscores, at most 64: " + reasonCode);
            }
        }
    }
}
