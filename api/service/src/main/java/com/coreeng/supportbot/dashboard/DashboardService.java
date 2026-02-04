package com.coreeng.supportbot.dashboard;

import com.coreeng.supportbot.dashboard.rest.EscalationAvgDurationByTag;
import com.coreeng.supportbot.dashboard.rest.EscalationByImpact;
import com.coreeng.supportbot.dashboard.rest.EscalationByTeam;
import com.coreeng.supportbot.dashboard.rest.EscalationCountByTag;
import com.coreeng.supportbot.dashboard.rest.EscalationCountTrend;
import com.coreeng.supportbot.dashboard.rest.WeeklyComparison;
import com.coreeng.supportbot.dashboard.rest.WeeklyTicketCounts;
import com.coreeng.supportbot.dashboard.rest.WeeklyTopEscalatedTag;
import com.coreeng.supportbot.dashboard.rest.ResolutionDurationBucket;
import com.coreeng.supportbot.dashboard.rest.ResolutionPercentiles;
import com.coreeng.supportbot.dashboard.rest.ResponsePercentiles;
import com.coreeng.supportbot.dashboard.rest.ResolutionIncomingResolvedRate;
import com.coreeng.supportbot.dashboard.rest.ResolutionOpenTicketAges;
import com.coreeng.supportbot.dashboard.rest.ResolutionTimeByTag;
import com.coreeng.supportbot.dashboard.rest.ResolutionWeeklyPercentiles;
import com.coreeng.supportbot.dashboard.rest.ResponseUnattendedCount;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private final DashboardRepository repository;

    public List<ResolutionDurationBucket> getResolutionDurationDistribution(String dateFrom, String dateTo) {
        return repository.getResolutionDurationDistribution(dateFrom, dateTo);
    }

    public ResolutionPercentiles getResolutionPercentiles(String dateFrom, String dateTo) {
        return repository.getResolutionPercentiles(dateFrom, dateTo);
    }

    public List<Integer> getResponseDistribution(String dateFrom, String dateTo) {
        return repository.getResponseDistribution(dateFrom, dateTo);
    }

    public ResponsePercentiles getResponsePercentiles(String dateFrom, String dateTo) {
        return repository.getResponsePercentiles(dateFrom, dateTo);
    }

    public ResponseUnattendedCount getResponseUnattendedCount(String dateFrom, String dateTo) {
        return repository.getResponseUnattendedCount(dateFrom, dateTo);
    }

    public List<ResolutionWeeklyPercentiles> getResolutionTimesByWeek(String dateFrom, String dateTo) {
        return repository.getResolutionTimesByWeek(dateFrom, dateTo);
    }

    public ResolutionOpenTicketAges getResolutionOpenTicketAges(String dateFrom, String dateTo) {
        return repository.getResolutionOpenTicketAges(dateFrom, dateTo);
    }

    public List<ResolutionTimeByTag> getResolutionTimeByTag(String dateFrom, String dateTo) {
        return repository.getResolutionTimeByTag(dateFrom, dateTo);
    }

    public List<ResolutionIncomingResolvedRate> getResolutionIncomingResolvedRate(String dateFrom, String dateTo) {
        return repository.getResolutionIncomingResolvedRate(dateFrom, dateTo);
    }

    public List<EscalationAvgDurationByTag> getEscalationAvgDurationByTag(String dateFrom, String dateTo) {
        return repository.getEscalationAvgDurationByTag(dateFrom, dateTo);
    }

    public List<EscalationCountByTag> getEscalationCountByTag(String dateFrom, String dateTo) {
        return repository.getEscalationCountByTag(dateFrom, dateTo);
    }

    public List<EscalationCountTrend> getEscalationCountTrend(String dateFrom, String dateTo) {
        return repository.getEscalationCountTrend(dateFrom, dateTo);
    }

    public List<EscalationByTeam> getEscalationByTeam(String dateFrom, String dateTo) {
        return repository.getEscalationByTeam(dateFrom, dateTo);
    }

    public List<EscalationByImpact> getEscalationByImpact(String dateFrom, String dateTo) {
        return repository.getEscalationByImpact(dateFrom, dateTo);
    }

    public List<WeeklyTicketCounts> getWeeklyTicketCounts(String dateFrom, String dateTo) {
        return repository.getWeeklyTicketCounts(dateFrom, dateTo);
    }

    public List<WeeklyComparison> getWeeklyComparison() {
        return repository.getWeeklyComparison();
    }

    public List<WeeklyTopEscalatedTag> getWeeklyTopEscalatedTags() {
        return repository.getWeeklyTopEscalatedTags();
    }

}
