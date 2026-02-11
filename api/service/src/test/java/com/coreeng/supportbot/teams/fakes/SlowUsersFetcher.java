package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class SlowUsersFetcher implements PlatformUsersFetcher {
    private final Duration delay;
    private final Function<String, List<Membership>> getResultFn;

    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger maxObserved = new AtomicInteger(0);

    public SlowUsersFetcher(Duration delay) {
        this(delay, groupRef -> Collections.emptyList());
    }

    public SlowUsersFetcher(Duration delay, Function<String, List<Membership>> getResultFn) {
        this.delay = delay;
        this.getResultFn = getResultFn;
    }

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        int now = inFlight.incrementAndGet();
        maxObserved.getAndAccumulate(now, Math::max);
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            inFlight.decrementAndGet();
        }
        return getResultFn.apply(groupRef);
    }

    public int maxObservedAtSameTime() {
        return maxObserved.get();
    }
}
