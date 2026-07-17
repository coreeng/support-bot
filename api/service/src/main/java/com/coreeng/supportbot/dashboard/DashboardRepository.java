package com.coreeng.supportbot.dashboard;

import com.coreeng.supportbot.dashboard.DashboardData.*;
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

    IncomingVsResolvedRate getIncomingVsResolvedRate(IncomingVsResolvedQuery query);

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
}
