package com.coreeng.supportbot.summary.rest;

import com.coreeng.supportbot.summary.SummaryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Support Summary page endpoint.
 *
 * <p>Serving this request can start a backfill and a summary regeneration server-side, which is why
 * it is readable by leadership as well as support engineers: the alternative would be to widen the
 * SUPPORT_ENGINEER-only {@code /analysis/run} permission to every viewer.
 */
@RestController
@RequestMapping("/summary")
@ConditionalOnProperty(name = "summary.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SummaryController {

    /** Default window length, in days, including both ends. */
    private static final int DEFAULT_WINDOW_DAYS = 14;

    /** Widest window that may be requested, to bound the LLM input and the SQL scan. */
    private static final long MAX_WINDOW_DAYS = 366;

    private final SummaryService summaryService;
    private final SummaryMapper summaryMapper;
    private final Clock clock;

    /**
     * @param from first day of the window; defaults with {@code to} to the last 14 days
     * @param to last day of the window; defaults to yesterday — today is unfinished, and excluding
     *     it keeps the window (and so the cached summary) stable for the whole day
     */
    @GetMapping
    public SummaryUI getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(clock).minusDays(1);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_WINDOW_DAYS - 1L);

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new IllegalArgumentException(
                    "'to' (" + resolvedTo + ") must not be before 'from' (" + resolvedFrom + ")");
        }
        // Both ends are included, so a window of from..to spans (to - from) + 1 days.
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1 > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException("The window must not exceed " + MAX_WINDOW_DAYS + " days");
        }

        return summaryMapper.mapToUI(summaryService.get(resolvedFrom, resolvedTo));
    }

    /** The in-use prompt the summary prose is generated with, for the page's View Prompts dialog. */
    @GetMapping("/prompt")
    public SummaryPromptResponse getPrompt() {
        return new SummaryPromptResponse(summaryService.promptContent());
    }

    public record SummaryPromptResponse(String prompt) {}
}
