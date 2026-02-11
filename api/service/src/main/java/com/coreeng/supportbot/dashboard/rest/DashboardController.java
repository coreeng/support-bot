package com.coreeng.supportbot.dashboard.rest;

import com.coreeng.supportbot.dashboard.DashboardRepository;
import com.coreeng.supportbot.dashboard.DashboardRepository.*;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for dashboard analytics and SLA metrics.
 * All endpoints return JSON data for the frontend dashboard.
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardRepository dashboardRepository;

    // ===== Response SLA Endpoints =====

    @GetMapping("/first-response-distribution")
    public List<Double> getFirstResponseDurationDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getFirstResponseDurationDistribution(dateFrom, dateTo);
    }

    @GetMapping("/first-response-percentiles")
    public ResponsePercentiles getFirstResponsePercentiles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getFirstResponsePercentiles(dateFrom, dateTo);
    }

    @GetMapping("/unattended-queries-count")
    public UnattendedCount getUnattendedQueriesCount(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return new UnattendedCount(dashboardRepository.getUnattendedQueriesCount(dateFrom, dateTo));
    }

    // ===== Resolution SLA Endpoints =====

    @GetMapping("/resolution-percentiles")
    public ResolutionPercentiles getResolutionPercentiles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getResolutionPercentiles(dateFrom, dateTo);
    }

    @GetMapping("/resolution-duration-distribution")
    public List<ResolutionDurationBucket> getResolutionDurationDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getResolutionDurationDistribution(dateFrom, dateTo);
    }

    @GetMapping("/resolution-times-by-week")
    public List<WeeklyResolutionTimes> getResolutionTimesByWeek(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getResolutionTimesByWeek(dateFrom, dateTo);
    }

    @GetMapping("/unresolved-ticket-ages")
    public UnresolvedTicketAges getUnresolvedTicketAges(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getUnresolvedTicketAges(dateFrom, dateTo);
    }

    @GetMapping("/incoming-vs-resolved-rate")
    public List<IncomingVsResolved> getIncomingVsResolvedRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getIncomingVsResolvedRate(dateFrom, dateTo);
    }

    // ===== Escalation SLA Endpoints =====

    @GetMapping("/avg-escalation-duration-by-tag")
    public List<TagDuration> getAvgEscalationDurationByTag(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getAvgEscalationDurationByTag(dateFrom, dateTo);
    }

    @GetMapping("/escalation-percentage-by-tag")
    public List<TagCount> getEscalationPercentageByTag(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getEscalationPercentageByTag(dateFrom, dateTo);
    }

    @GetMapping("/escalation-trends-by-date")
    public List<DateEscalations> getEscalationTrendsByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getEscalationTrendsByDate(dateFrom, dateTo);
    }

    @GetMapping("/escalations-by-team")
    public List<TeamEscalations> getEscalationsByTeam(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getEscalationsByTeam(dateFrom, dateTo);
    }

    @GetMapping("/escalations-by-impact")
    public List<ImpactEscalations> getEscalationsByImpact(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getEscalationsByImpact(dateFrom, dateTo);
    }

    // ===== Weekly Trends Endpoints =====

    @GetMapping("/weekly-ticket-counts")
    public List<WeeklyTicketCounts> getWeeklyTicketCounts() {
        return dashboardRepository.getWeeklyTicketCounts();
    }

    @GetMapping("/weekly-comparison")
    public List<WeeklyComparison> getWeeklyComparison() {
        return dashboardRepository.getWeeklyComparison();
    }

    @GetMapping("/top-escalated-tags-this-week")
    public List<TagCount> getTopEscalatedTagsThisWeek() {
        return dashboardRepository.getTopEscalatedTagsThisWeek();
    }

    @GetMapping("/resolution-time-by-tag")
    public List<TagResolutionTime> getResolutionTimeByTag(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return dashboardRepository.getResolutionTimeByTag(dateFrom, dateTo);
    }

    // Response wrapper for count
    public record UnattendedCount(long count) {}
}
