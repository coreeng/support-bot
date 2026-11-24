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
    EscalationRate getEscalationRate(LocalDate dateFrom, LocalDate dateTo);

    List<ResolutionByEscalation> getResolutionEscalatedVsNot(LocalDate dateFrom, LocalDate dateTo);

    List<TeamScorecard> getEscalationTeamScorecard(LocalDate dateFrom, LocalDate dateTo);

    List<EscalationTopic> getEscalationTopics(LocalDate dateFrom, LocalDate dateTo);

    TimeBeforeEscalation getTimeBeforeEscalation(LocalDate dateFrom, LocalDate dateTo);

    List<TeamEscalationRate> getEscalationRateByTeam(LocalDate dateFrom, LocalDate dateTo);

    // Weekly Trends
    List<WeeklyTicketCounts> getWeeklyTicketCounts();

    List<WeeklyComparison> getWeeklyComparison();

    List<TagCount> getTopEscalatedTagsThisWeek();

    List<TagResolutionTime> getResolutionTimeByTag(LocalDate dateFrom, LocalDate dateTo);
}
