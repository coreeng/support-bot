package com.coreeng.supportbot.dashboard.rest;

import com.coreeng.supportbot.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/response/distribution")
    public List<Integer> getResponseDistribution(@RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        return service.getResponseDistribution(dateFrom, dateTo);
    }

    @GetMapping("/response/percentiles")
    public ResponsePercentiles getResponsePercentiles(@RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        return service.getResponsePercentiles(dateFrom, dateTo);
    }

    @GetMapping("/response/unattended-count")
    public ResponseUnattendedCount getResponseUnattendedCount(@RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        return service.getResponseUnattendedCount(dateFrom, dateTo);
    }

    @GetMapping("/resolution/duration-distribution")
    public List<ResolutionDurationBucket> getResolutionDurationDistribution(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getResolutionDurationDistribution(dateFrom, dateTo);
    }

    @GetMapping("/resolution/percentiles")
    public ResolutionPercentiles getResolutionPercentiles(@RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        return service.getResolutionPercentiles(dateFrom, dateTo);
    }

    @GetMapping("/resolution/times-by-week")
    public List<ResolutionWeeklyPercentiles> getResolutionTimesByWeek(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getResolutionTimesByWeek(dateFrom, dateTo);
    }

    @GetMapping("/resolution/open-ticket-ages")
    public ResolutionOpenTicketAges getResolutionOpenTicketAges(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getResolutionOpenTicketAges(dateFrom, dateTo);
    }

    @GetMapping("/resolution/time-by-tag")
    public List<ResolutionTimeByTag> getResolutionTimeByTag(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getResolutionTimeByTag(dateFrom, dateTo);
    }

    @GetMapping("/resolution/incoming-resolved-rate")
    public List<ResolutionIncomingResolvedRate> getResolutionIncomingResolvedRate(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getResolutionIncomingResolvedRate(dateFrom, dateTo);
    }

    @GetMapping("/escalation/avg-duration-by-tag")
    public List<EscalationAvgDurationByTag> getEscalationAvgDurationByTag(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getEscalationAvgDurationByTag(dateFrom, dateTo);
    }

    @GetMapping("/escalation/count-by-tag")
    public List<EscalationCountByTag> getEscalationCountByTag(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getEscalationCountByTag(dateFrom, dateTo);
    }

    @GetMapping("/escalation/count-trend")
    public List<EscalationCountTrend> getEscalationCountTrend(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getEscalationCountTrend(dateFrom, dateTo);
    }

    @GetMapping("/escalation/by-team")
    public List<EscalationByTeam> getEscalationByTeam(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getEscalationByTeam(dateFrom, dateTo);
    }

    @GetMapping("/escalation/by-impact")
    public List<EscalationByImpact> getEscalationByImpact(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getEscalationByImpact(dateFrom, dateTo);
    }

    @GetMapping("/weekly/ticket-counts")
    public List<WeeklyTicketCounts> getWeeklyTicketCounts(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return service.getWeeklyTicketCounts(dateFrom, dateTo);
    }

    @GetMapping("/weekly/comparison")
    public List<WeeklyComparison> getWeeklyComparison() {
        return service.getWeeklyComparison();
    }

    @GetMapping("/weekly/top-escalated-tags")
    public List<WeeklyTopEscalatedTag> getWeeklyTopEscalatedTags() {
        return service.getWeeklyTopEscalatedTags();
    }

}
