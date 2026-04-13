package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dashboard.DashboardRepository.IncomingVsResolvedGranularity;
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
        List<DashboardRepository.IncomingVsResolved> data = new ArrayList<>();
        data.add(new DashboardRepository.IncomingVsResolved("2026-01-01T00:00:00Z", 1, 2));

        DashboardRepository.IncomingVsResolvedRate rate =
                new DashboardRepository.IncomingVsResolvedRate(IncomingVsResolvedGranularity.DAY, data);
        data.clear();

        assertThat(rate.data()).hasSize(1);
    }
}
