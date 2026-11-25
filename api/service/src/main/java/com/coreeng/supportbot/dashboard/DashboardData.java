package com.coreeng.supportbot.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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

    /** Bucket size of a dashboard time series. */
    public enum Granularity {
        HOUR,
        DAY,
        WEEK;

        private static final long MAX_DAILY_RANGE_DAYS = 31;

        /** By day for windows up to a month; by week for longer or unbounded windows. */
        public static Granularity dayOrWeekForRange(@Nullable LocalDate from, @Nullable LocalDate to) {
            if (from == null || to == null) {
                return WEEK;
            }
            return to.isAfter(from.plusDays(MAX_DAILY_RANGE_DAYS)) ? WEEK : DAY;
        }

        /** The Postgres {@code date_trunc} / interval unit for this granularity. */
        public String unit() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record IncomingVsResolvedRate(Granularity granularity, List<IncomingVsResolved> data) {
        public IncomingVsResolvedRate {
            data = List.copyOf(data);
        }
    }

    public record TagCount(String tag, long count) {}

    /** Share of tickets opened in a bucket that were escalated; {@code rate} is a fraction in [0, 1]. */
    public record EscalationRatePoint(String date, long tickets, long escalatedTickets, double rate) {}

    public record EscalationRate(Granularity granularity, List<EscalationRatePoint> data) {
        public EscalationRate {
            data = List.copyOf(data);
        }
    }

    /** Ticket resolution percentiles, in seconds, for escalated or never-escalated closed tickets. */
    public record ResolutionByEscalation(boolean escalated, long tickets, double p50, double p90) {}

    /** Per receiving team; resolution percentiles are null when nothing is resolved yet. */
    public record TeamScorecard(
            String team,
            long escalations,
            long open,
            @Nullable Double resolutionP50,
            @Nullable Double resolutionP90) {}

    /**
     * Escalation tag (by code) ranked by total time of its resolved escalations; {@code avgWaitSeconds} is null
     * when none of them was manual.
     */
    public record EscalationTopic(
            String tag,
            long escalations,
            double avgSeconds,
            double totalSeconds,
            @Nullable Double avgWaitSeconds) {}

    /** Time from a ticket opening to its first manual escalation. */
    public record TimeBeforeEscalation(long escalations, double p50, double p90) {}

    /** Share of a requesting team's tickets, opened in the window, that were escalated at least once. */
    public record TeamEscalationRate(String team, long tickets, long escalatedTickets, double rate) {}

    public record WeeklyTicketCounts(String week, long opened, long closed, long escalated, long stale) {}

    public record WeeklyComparison(String label, long thisWeek, long lastWeek, long change) {}

    public record TagResolutionTime(String tag, double p50, double p90) {}
}
