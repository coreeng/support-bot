package com.coreeng.supportbot.docanalysis.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The Documentation Analysis page payload. Every figure covers the closed tickets raised in the
 * window in a monitored channel, and only results under the current prompt.
 *
 * @param from first day of the window (inclusive)
 * @param to last day of the window (inclusive)
 * @param productAreas the classification counts per area each ticket counts under; tickets not yet
 *     classified have no area and are counted only in {@code counts}
 * @param citedPages {@code correct} and {@code incorrect} results by the page they cite
 * @param missingByArea {@code missing} results by the area they count under
 * @param results {@code incorrect} first, then {@code missing}, {@code correct} and {@code
 *     not_applicable}; newest ticket first within each
 * @param documentation what the source holds now; absent when it holds nothing yet
 */
public record DocAnalysisUI(
        LocalDate from,
        LocalDate to,
        CountsUI counts,
        List<AreaCountsUI> productAreas,
        List<PageGroupUI> citedPages,
        List<AreaGroupUI> missingByArea,
        List<ResultUI> results,
        RunUI run,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable DocumentationUI documentation) {

    /**
     * @param total closed tickets in the window
     * @param unclassified those without a current result, so that the classifications and this sum
     *     to {@code total}
     * @param failed those of {@code unclassified} whose last attempt failed
     */
    public record CountsUI(
            long total,
            long correct,
            long missing,
            long incorrect,
            long notApplicable,
            long unclassified,
            long failed) {}

    /** @param area absent for the tickets that count under no area */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AreaCountsUI(
            @Nullable String area, long total, long correct, long missing, long incorrect, long notApplicable) {}

    /** @param ticketIds in the order of {@code results} */
    public record PageGroupUI(String page, long count, long correct, long incorrect, List<String> ticketIds) {}

    /**
     * @param area absent for the tickets that count under no area
     * @param ticketIds in the order of {@code results}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AreaGroupUI(@Nullable String area, long count, List<String> ticketIds) {}

    /**
     * @param ticketId the ticket, as a string to match the rest of the UI's ticket handling
     * @param channelId the Slack channel of the thread
     * @param queryTs the Slack timestamp of the thread's opening message
     * @param raisedAt when the ticket was raised
     * @param classification {@code correct}, {@code missing}, {@code incorrect} or {@code
     *     not_applicable}
     * @param answerSummary what support answered, when the model gave it
     * @param docPaths for {@code correct} and {@code incorrect} the one page cited; for {@code
     *     missing} the closest pages, if any; empty for {@code not_applicable}
     * @param evidenceQuote the quoted passage of the cited page, for {@code correct} and {@code
     *     incorrect}
     * @param productArea the area the ticket counts under; absent for none
     * @param productAreas the areas the ticket counts under, {@code productArea} first
     * @param docsVersion the documentation version the result was made against
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultUI(
            String ticketId,
            String channelId,
            String queryTs,
            Instant raisedAt,
            String classification,
            String question,
            @Nullable String answerSummary,
            List<String> tags,
            List<String> docPaths,
            @Nullable String evidenceQuote,
            String rationale,
            @Nullable String productArea,
            List<String> productAreas,
            @Nullable String docsVersion,
            Instant analysedAt) {}

    /**
     * @param state {@code running}, {@code busy}, {@code idle}, {@code source_unavailable}, {@code
     *     provider_outage}, {@code daily_limit_reached}, {@code failed} or {@code no_prompt}
     * @param progress present when running, and when busy with a run this instance holds
     * @param reason present when the source is unavailable; safe to show
     * @param retryAt present during a provider outage: no run starts before it
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunUI(
            String state,
            @Nullable ProgressUI progress,
            @Nullable String reason,
            @Nullable Instant retryAt) {}

    /**
     * @param from first day of the run's window
     * @param to last day of the run's window
     * @param done tickets attempted so far
     * @param total tickets due when the run started; zero until it has counted them
     */
    public record ProgressUI(LocalDate from, LocalDate to, int done, int total) {}

    /**
     * @param version the version the source holds; its meaning belongs to the source
     * @param refreshedAt when the source last refreshed what it holds
     */
    public record DocumentationUI(String version, Instant refreshedAt) {}
}
