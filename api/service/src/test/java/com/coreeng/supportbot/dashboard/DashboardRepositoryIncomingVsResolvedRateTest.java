package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolvedGranularity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardRepositoryIncomingVsResolvedRateTest {

    @Test
    void responseGranularityEnumDoesNotExposeAuto() {
        assertThat(IncomingVsResolvedGranularity.values())
                .containsExactly(
                        IncomingVsResolvedGranularity.HOUR,
                        IncomingVsResolvedGranularity.DAY,
                        IncomingVsResolvedGranularity.WEEK);
    }

    @Test
    void defensivelyCopiesResponseData() {
        List<DashboardData.IncomingVsResolved> data = new ArrayList<>();
        data.add(new DashboardData.IncomingVsResolved("2026-01-01T00:00:00Z", 1, 2));

        DashboardData.IncomingVsResolvedRate rate =
                new DashboardData.IncomingVsResolvedRate(IncomingVsResolvedGranularity.DAY, data);
        data.clear();

        assertThat(rate.data()).hasSize(1);
    }
}
