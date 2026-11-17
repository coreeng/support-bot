package com.coreeng.supportbot.dashboard;

import java.util.List;
import org.jspecify.annotations.Nullable;

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

    public record TagCount(String tag, long count) {}

    public enum EscalationRateGranularity {
        DAY,
        WEEK
    }

    /**
     * Share of the tickets opened in a bucket that were escalated at least once. {@code rate} is a fraction in
     * [0, 1] and is zero when the bucket has no tickets.
     */
    public record EscalationRatePoint(String date, long tickets, long escalatedTickets, double rate) {}

    public record EscalationRate(EscalationRateGranularity granularity, List<EscalationRatePoint> data) {
        public EscalationRate {
            data = List.copyOf(data);
        }
    }

    /** Ticket resolution percentiles (business seconds) for escalated or never-escalated closed tickets. */
    public record ResolutionByEscalation(boolean escalated, long tickets, double p50, double p90) {}

    /**
     * Per receiving team: escalations opened in the window, how many of those are still open, and resolution
     * percentiles (business seconds, resolved escalations only; null when none are resolved yet).
     */
    public record TeamScorecard(
            String team,
            long escalations,
            long open,
            @Nullable Double resolutionP50,
            @Nullable Double resolutionP90) {}

    /**
     * Escalation tag ranked by total business time spent in escalation ({@code escalations × avgSeconds}),
     * resolved escalations only.
     */
    public record EscalationTopic(String tag, long escalations, double avgSeconds, double totalSeconds) {}

    public record ImpactEscalations(String impactLevel, long totalEscalations) {}

    public record WeeklyTicketCounts(String week, long opened, long closed, long escalated, long stale) {}

    public record WeeklyComparison(String label, long thisWeek, long lastWeek, long change) {}

    public record TagResolutionTime(String tag, double p50, double p90) {}
}
