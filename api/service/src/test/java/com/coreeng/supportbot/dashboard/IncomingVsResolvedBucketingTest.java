package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dashboard.DashboardRepository.IncomingVsResolvedGranularity;
import com.coreeng.supportbot.dashboard.JdbcDashboardRepository.IncomingVsResolvedBucketing;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IncomingVsResolvedBucketingTest {

    @Test
    void preservesLegacyHourlyBucketingWhenGranularityIsOmitted() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        IncomingVsResolvedBucketing bucketing = IncomingVsResolvedBucketing.forDateRange(start, start.plusDays(60));

        assertBucketing(bucketing, IncomingVsResolvedGranularity.HOUR, "'1 hour'::interval", "hour");
    }

    @Test
    void preservesLegacyDailyBucketingWhenGranularityIsOmitted() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        IncomingVsResolvedBucketing bucketing = IncomingVsResolvedBucketing.forDateRange(start, start.plusDays(61));

        assertBucketing(bucketing, IncomingVsResolvedGranularity.DAY, "'1 day'::interval", "day");
    }

    @Test
    void usesAutoHourlyBucketsForWindowsUpToFourDays() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        IncomingVsResolvedBucketing bucketing = IncomingVsResolvedBucketing.forDateRange(
                start, start.plusDays(4), IncomingVsResolvedQuery.Granularity.AUTO);

        assertBucketing(bucketing, IncomingVsResolvedGranularity.HOUR, "'1 hour'::interval", "hour");
    }

    @Test
    void usesAutoDailyBucketsForWindowsLongerThanFourDaysAndUpToTwoMonths() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        IncomingVsResolvedBucketing bucketing = IncomingVsResolvedBucketing.forDateRange(
                start, start.plusMonths(2), IncomingVsResolvedQuery.Granularity.AUTO);

        assertBucketing(bucketing, IncomingVsResolvedGranularity.DAY, "'1 day'::interval", "day");
    }

    @Test
    void usesAutoWeeklyBucketsForWindowsLongerThanTwoMonths() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        IncomingVsResolvedBucketing bucketing = IncomingVsResolvedBucketing.forDateRange(
                start, start.plusMonths(2).plusDays(1), IncomingVsResolvedQuery.Granularity.AUTO);

        assertBucketing(bucketing, IncomingVsResolvedGranularity.WEEK, "'1 week'::interval", "week");
    }

    @Test
    void honoursExplicitGranularityOverrides() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        IncomingVsResolvedBucketing bucketing = IncomingVsResolvedBucketing.forDateRange(
                start, start.plusMonths(3), IncomingVsResolvedQuery.Granularity.DAY);

        assertBucketing(bucketing, IncomingVsResolvedGranularity.DAY, "'1 day'::interval", "day");
    }

    @Test
    void defaultsEmptyResultsToDailyWhenGranularityIsNull() {
        assertThat(JdbcDashboardRepository.resolveEmptyResultGranularity(null))
                .isEqualTo(IncomingVsResolvedGranularity.DAY);
    }

    @Test
    void defaultsEmptyResultsToDailyWhenGranularityIsAuto() {
        assertThat(JdbcDashboardRepository.resolveEmptyResultGranularity(IncomingVsResolvedQuery.Granularity.AUTO))
                .isEqualTo(IncomingVsResolvedGranularity.DAY);
    }

    @Test
    void preservesExplicitEmptyResultHourlyGranularity() {
        assertThat(JdbcDashboardRepository.resolveEmptyResultGranularity(IncomingVsResolvedQuery.Granularity.HOUR))
                .isEqualTo(IncomingVsResolvedGranularity.HOUR);
    }

    @Test
    void preservesExplicitEmptyResultWeeklyGranularity() {
        assertThat(JdbcDashboardRepository.resolveEmptyResultGranularity(IncomingVsResolvedQuery.Granularity.WEEK))
                .isEqualTo(IncomingVsResolvedGranularity.WEEK);
    }

    private static void assertBucketing(
            IncomingVsResolvedBucketing bucketing,
            IncomingVsResolvedGranularity granularity,
            String interval,
            String truncUnit) {
        assertThat(bucketing.granularity()).isEqualTo(granularity);
        assertThat(bucketing.interval()).isEqualTo(interval);
        assertThat(bucketing.incomingTrunc()).contains("date_trunc('" + truncUnit + "'");
        assertThat(bucketing.resolvedTrunc()).contains("date_trunc('" + truncUnit + "'");
    }
}
