package com.coreeng.supportbot.stats;

public interface StatsCollector<T extends StatsRequest> {
    StatsType getSupportedType();

    StatsResult calculateResults(T request);

    @SuppressWarnings("unchecked")
    default StatsResult calculateResultsUnchecked(StatsRequest request) {
        return calculateResults((T) request);
    }
}
