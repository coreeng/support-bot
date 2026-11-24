package com.coreeng.supportbot.docanalysis.rest;

import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.docanalysis.DocAnalysisReportService;
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
 * The Documentation Analysis page endpoint. Serving it can start a run over the window, so its
 * window rules and roles are those of {@code /summary}.
 */
@RestController
@RequestMapping("/documentation-analysis")
@ConditionalOnLlmEnabled
@ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DocAnalysisController {

    private static final int DEFAULT_WINDOW_DAYS = 14;
    private static final long MAX_WINDOW_DAYS = 92;

    private final DocAnalysisReportService reportService;
    private final DocAnalysisMapper mapper;
    private final Clock clock;

    /**
     * @param from first day of the window; defaults with {@code to} to the last 14 days
     * @param to last day of the window; defaults to yesterday
     */
    @GetMapping
    public DocAnalysisUI getDocumentationAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(clock).minusDays(1);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_WINDOW_DAYS - 1L);

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new InvalidDocAnalysisWindowException(
                    "'to' (" + resolvedTo + ") must not be before 'from' (" + resolvedFrom + ")");
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1 > MAX_WINDOW_DAYS) {
            throw new InvalidDocAnalysisWindowException(
                    "The window must not exceed " + MAX_WINDOW_DAYS + " days (about one quarter)");
        }

        return mapper.mapToUI(reportService.report(resolvedFrom, resolvedTo));
    }
}
