package com.coreeng.supportbot.docanalysis;

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public sealed interface ContextAnswer {

    /**
     * The pages to classify the thread against, in the order they are to be sent.
     *
     * @param areasConsulted the product areas whose pages are among {@code pages}, most likely first;
     *     pages that belong to no area are not counted. Empty for a source without areas
     * @param areasNotSupplied product areas that exist and whose pages are not among {@code pages}
     * @param question null when the source did not distil one; the classification call then does
     */
    record Pages(
            List<DocumentationPage> pages,
            List<String> areasConsulted,
            List<String> areasNotSupplied,
            @Nullable DistilledQuestion question)
            implements ContextAnswer {

        public Pages {
            pages = List.copyOf(pages);
            areasConsulted = List.copyOf(areasConsulted);
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
            Set<String> consulted = new LinkedHashSet<>(areasConsulted);
            if (consulted.size() != areasConsulted.size()) {
                throw new IllegalArgumentException("areasConsulted must not repeat an area: " + areasConsulted);
            }
            Set<String> pageAreas = new HashSet<>();
            pages.stream().map(DocumentationPage::area).filter(Objects::nonNull).forEach(pageAreas::add);
            if (!pageAreas.equals(consulted)) {
                throw new IllegalArgumentException(
                        "areasConsulted " + areasConsulted + " must be exactly the areas of the pages " + pageAreas);
            }
            for (String area : areasNotSupplied) {
                if (area.isBlank()) {
                    throw new IllegalArgumentException("areasNotSupplied must not hold a blank name");
                }
                if (consulted.contains(area)) {
                    throw new IllegalArgumentException("area is both consulted and not supplied: " + area);
                }
            }
        }

        public int estimatedTokens() {
            return pages.stream()
                    .mapToInt(page -> TokenEstimator.estimate(page.text()))
                    .sum();
        }
    }

    /**
     * The thread asks a documentation question that nothing in the documentation covers. Nothing
     * later distils the question, so it is required.
     */
    record NothingCovers(String reason, DistilledQuestion question) implements ContextAnswer {
        public NothingCovers {
            requireReasonAndQuestion(reason, question);
        }
    }

    /** The thread asks no documentation question. Nothing later distils the question, so it is required. */
    record NotADocumentationQuestion(String reason, DistilledQuestion question) implements ContextAnswer {
        public NotADocumentationQuestion {
            requireReasonAndQuestion(reason, question);
        }
    }

    /**
     * @param reasonCode stored as the result's failure reason, so it is a fixed code, never model or
     *     provider text
     * @param retryable false when trying again cannot help until the prompts or settings change
     */
    record Failed(String reasonCode, boolean retryable) implements ContextAnswer {

        /** A model call failed after its retries. The run's outage stop counts tickets ending in it. */
        public static final String PROVIDER_ERROR = "provider_error";

        /** A model answered and the answer could not be read or broke its contract. */
        public static final String INVALID_RESPONSE = "invalid_response";

        private static final Pattern REASON_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        public Failed {
            if (!REASON_CODE.matcher(reasonCode).matches()) {
                throw new IllegalArgumentException(
                        "reasonCode must be lowercase letters, digits and underscores, at most 64: " + reasonCode);
            }
        }

        public static Failed providerError() {
            return new Failed(PROVIDER_ERROR, true);
        }

        public static Failed invalidResponse() {
            return new Failed(INVALID_RESPONSE, true);
        }
    }

    private static void requireReasonAndQuestion(String reason, DistilledQuestion question) {
        requireNonNull(question, "question");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
