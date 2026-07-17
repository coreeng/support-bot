package com.coreeng.supportbot.dashboard;

import java.util.List;

/** Transport-neutral result types returned by dashboard analytics queries. */
public final class DashboardData {
    private DashboardData() {}

    public record ResponsePercentiles(double p50, double p90) {}

    public record ResolutionPercentiles(double p50, double p75, double p90) {}

    public record ResolutionDurationBucket(String label, long count, double minMinutes, double maxMinutes) {}

    public record WeeklyResolutionTimes(String week, double p50, double p75, double p90) {}

    public record UnresolvedTicketAges(String p50, String p90) {}

    public record IncomingVsResolved(String time, long incoming, long resolved) {}

    public enum IncomingVsResolvedGranularity {
        HOUR,
        DAY,
        WEEK
    }

    public record IncomingVsResolvedRate(IncomingVsResolvedGranularity granularity, List<IncomingVsResolved> data) {
        public IncomingVsResolvedRate {
            data = List.copyOf(data);
        }
    }

    public record TagDuration(String tag, double avgDuration) {}

    public record TagCount(String tag, long count) {}

    public record DateEscalations(String date, long escalations) {}

    public record TeamEscalations(String assigneeName, long totalEscalations) {}

    public record ImpactEscalations(String impactLevel, long totalEscalations) {}

    public record WeeklyTicketCounts(String week, long opened, long closed, long escalated, long stale) {}

    public record WeeklyComparison(String label, long thisWeek, long lastWeek, long change) {}

    public record TagResolutionTime(String tag, double p50, double p90) {}
}
