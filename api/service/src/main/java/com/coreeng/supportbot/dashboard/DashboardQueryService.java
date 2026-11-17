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

    public EscalationRate getEscalationRate(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationRate(dateFrom, dateTo);
    }

    public List<ResolutionByEscalation> getResolutionEscalatedVsNot(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getResolutionEscalatedVsNot(dateFrom, dateTo);
    }

    public List<TeamScorecard> getEscalationTeamScorecard(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationTeamScorecard(dateFrom, dateTo);
    }

    public List<EscalationTopic> getEscalationTopics(LocalDate dateFrom, LocalDate dateTo) {
        return dashboardRepository.getEscalationTopics(dateFrom, dateTo);
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
