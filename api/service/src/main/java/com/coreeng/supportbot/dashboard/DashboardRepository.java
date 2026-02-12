package com.coreeng.supportbot.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for dashboard analytics and SLA metrics.
 * All queries use aggregated_ticket_data and aggregated_escalation_data views.
 */
public interface DashboardRepository {

    // Response SLAs
    List<Double> getFirstResponseDurationDistribution(LocalDate dateFrom, LocalDate dateTo);

    ResponsePercentiles getFirstResponsePercentiles(LocalDate dateFrom, LocalDate dateTo);

    long getUnattendedQueriesCount(LocalDate dateFrom, LocalDate dateTo);

    // Resolution SLAs
    ResolutionPercentiles getResolutionPercentiles(LocalDate dateFrom, LocalDate dateTo);

    List<ResolutionDurationBucket> getResolutionDurationDistribution(LocalDate dateFrom, LocalDate dateTo);

    List<WeeklyResolutionTimes> getResolutionTimesByWeek(LocalDate dateFrom, LocalDate dateTo);

    UnresolvedTicketAges getUnresolvedTicketAges(LocalDate dateFrom, LocalDate dateTo);

    List<IncomingVsResolved> getIncomingVsResolvedRate(LocalDate dateFrom, LocalDate dateTo);

    // Escalation SLAs
    List<TagDuration> getAvgEscalationDurationByTag(LocalDate dateFrom, LocalDate dateTo);

    List<TagCount> getEscalationPercentageByTag(LocalDate dateFrom, LocalDate dateTo);

    List<DateEscalations> getEscalationTrendsByDate(LocalDate dateFrom, LocalDate dateTo);

    List<TeamEscalations> getEscalationsByTeam(LocalDate dateFrom, LocalDate dateTo);

    List<ImpactEscalations> getEscalationsByImpact(LocalDate dateFrom, LocalDate dateTo);

    // Weekly Trends
    List<WeeklyTicketCounts> getWeeklyTicketCounts();

    List<WeeklyComparison> getWeeklyComparison();

    List<TagCount> getTopEscalatedTagsThisWeek();

    List<TagResolutionTime> getResolutionTimeByTag(LocalDate dateFrom, LocalDate dateTo);

    // DTOs
    record ResponsePercentiles(double p50, double p90) {}

    record ResolutionPercentiles(double p50, double p75, double p90) {}

    record ResolutionDurationBucket(String label, long count, double minMinutes, double maxMinutes) {}

    record WeeklyResolutionTimes(String week, double p50, double p75, double p90) {}

    record UnresolvedTicketAges(String p50, String p90) {}

    record IncomingVsResolved(String time, long incoming, long resolved) {}

    record TagDuration(String tag, double avgDuration) {}

    record TagCount(String tag, long count) {}

    record DateEscalations(String date, long escalations) {}

    record TeamEscalations(String assigneeName, long totalEscalations) {}

    record ImpactEscalations(String impactLevel, long totalEscalations) {}

    record WeeklyTicketCounts(String week, long opened, long closed, long escalated, long stale) {}

    record WeeklyComparison(String label, long thisWeek, long lastWeek, long change) {}

    record TagResolutionTime(String tag, double p50, double p90) {}
}
