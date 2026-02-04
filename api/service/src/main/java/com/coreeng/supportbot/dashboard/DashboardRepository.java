package com.coreeng.supportbot.dashboard;

import com.coreeng.supportbot.dashboard.rest.EscalationAvgDurationByTag;
import com.coreeng.supportbot.dashboard.rest.EscalationByImpact;
import com.coreeng.supportbot.dashboard.rest.EscalationByTeam;
import com.coreeng.supportbot.dashboard.rest.EscalationCountByTag;
import com.coreeng.supportbot.dashboard.rest.EscalationCountTrend;
import com.coreeng.supportbot.dashboard.rest.WeeklyComparison;
import com.coreeng.supportbot.dashboard.rest.WeeklyTopEscalatedTag;
import com.coreeng.supportbot.dashboard.rest.WeeklyTicketCounts;
import com.coreeng.supportbot.dashboard.rest.ResolutionDurationBucket;
import com.coreeng.supportbot.dashboard.rest.ResolutionPercentiles;
import com.coreeng.supportbot.dashboard.rest.ResponsePercentiles;
import com.coreeng.supportbot.dashboard.rest.ResponseUnattendedCount;
import com.coreeng.supportbot.dashboard.rest.ResolutionIncomingResolvedRate;
import com.coreeng.supportbot.dashboard.rest.ResolutionOpenTicketAges;
import com.coreeng.supportbot.dashboard.rest.ResolutionTimeByTag;
import com.coreeng.supportbot.dashboard.rest.ResolutionWeeklyPercentiles;

import java.util.List;

public interface DashboardRepository {
    List<Integer> getResponseDistribution(String dateFrom, String dateTo);
    ResponsePercentiles getResponsePercentiles(String dateFrom, String dateTo);
    ResponseUnattendedCount getResponseUnattendedCount(String dateFrom, String dateTo);
    List<ResolutionDurationBucket> getResolutionDurationDistribution(String dateFrom, String dateTo);
    ResolutionPercentiles getResolutionPercentiles(String dateFrom, String dateTo);
    List<ResolutionWeeklyPercentiles> getResolutionTimesByWeek(String dateFrom, String dateTo);
    ResolutionOpenTicketAges getResolutionOpenTicketAges(String dateFrom, String dateTo);
    List<ResolutionIncomingResolvedRate> getResolutionIncomingResolvedRate(String dateFrom, String dateTo);
    List<ResolutionTimeByTag> getResolutionTimeByTag(String dateFrom, String dateTo);
    List<EscalationAvgDurationByTag> getEscalationAvgDurationByTag(String dateFrom, String dateTo);
    List<EscalationCountByTag> getEscalationCountByTag(String dateFrom, String dateTo);
    List<EscalationCountTrend> getEscalationCountTrend(String dateFrom, String dateTo);
    List<EscalationByTeam> getEscalationByTeam(String dateFrom, String dateTo);
    List<EscalationByImpact> getEscalationByImpact(String dateFrom, String dateTo);
    List<WeeklyTicketCounts> getWeeklyTicketCounts(String dateFrom, String dateTo);
    List<WeeklyComparison> getWeeklyComparison();
    List<WeeklyTopEscalatedTag> getWeeklyTopEscalatedTags();
}