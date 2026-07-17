package com.coreeng.supportbot.dashboard;

import com.coreeng.supportbot.dashboard.DashboardData.*;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application-facing query boundary for dashboard analytics. */
@Service
@RequiredArgsConstructor
public class DashboardQueryService {
    private final DashboardRepository dashboardRepository;

    public List<Double> getFirstResponseDurationDistribution(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getFirstResponseDurationDistribution(dateFrom, dateTo);
    }

    public ResponsePercentiles getFirstResponsePercentiles(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getFirstResponsePercentiles(dateFrom, dateTo);
    }

    public long getUnattendedQueriesCount(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getUnattendedQueriesCount(dateFrom, dateTo);
    }

    public ResolutionPercentiles getResolutionPercentiles(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getResolutionPercentiles(dateFrom, dateTo);
    }

    public List<ResolutionDurationBucket> getResolutionDurationDistribution(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getResolutionDurationDistribution(dateFrom, dateTo);
    }

    public List<WeeklyResolutionTimes> getResolutionTimesByWeek(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getResolutionTimesByWeek(dateFrom, dateTo);
    }

    public UnresolvedTicketAges getUnresolvedTicketAges(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getUnresolvedTicketAges(dateFrom, dateTo);
    }

    public IncomingVsResolvedRate getIncomingVsResolvedRate(IncomingVsResolvedQuery query) {
        return dashboardRepository.getIncomingVsResolvedRate(query);
    }

    public List<TagDuration> getAvgEscalationDurationByTag(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getAvgEscalationDurationByTag(dateFrom, dateTo);
    }

    public List<TagCount> getEscalationPercentageByTag(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationPercentageByTag(dateFrom, dateTo);
    }

    public List<DateEscalations> getEscalationTrendsByDate(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationTrendsByDate(dateFrom, dateTo);
    }

    public List<TeamEscalations> getEscalationsByTeam(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationsByTeam(dateFrom, dateTo);
    }

    public List<ImpactEscalations> getEscalationsByImpact(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationsByImpact(dateFrom, dateTo);
    }

    public List<WeeklyTicketCounts> getWeeklyTicketCounts() {
        return dashboardRepository.getWeeklyTicketCounts();
    }

    public List<WeeklyComparison> getWeeklyComparison() {
        return dashboardRepository.getWeeklyComparison();
    }

    public List<TagCount> getTopEscalatedTagsThisWeek() {
        return dashboardRepository.getTopEscalatedTagsThisWeek();
    }

    public List<TagResolutionTime> getResolutionTimeByTag(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getResolutionTimeByTag(dateFrom, dateTo);
    }
}
