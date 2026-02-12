package com.coreeng.supportbot.stats;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class StatsService {
    private final ImmutableMap<StatsType, StatsCollector<?>> statsCollectorMap;

    public StatsService(List<StatsCollector<?>> statsCollectorMap) {
        this.statsCollectorMap = statsCollectorMap.stream()
                .collect(toImmutableMap(StatsCollector::getSupportedType, Function.identity()));
    }

    public StatsResult calculate(StatsRequest request) {
        StatsCollector<?> collector = statsCollectorMap.get(request.type());
        if (collector == null) {
            throw new IllegalStateException("No collector for stats type: " + request.type());
        }
        return collector.calculateResultsUnchecked(request);
    }
}
