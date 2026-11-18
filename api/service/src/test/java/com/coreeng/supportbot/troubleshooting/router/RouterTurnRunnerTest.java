package com.coreeng.supportbot.troubleshooting.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.troubleshooting.Payloads.RouterStatus;
import com.coreeng.supportbot.troubleshooting.TroubleshootingEvent;
import com.coreeng.supportbot.troubleshooting.TroubleshootingEventType;
import com.coreeng.supportbot.troubleshooting.TroubleshootingExecutor;
import com.coreeng.supportbot.troubleshooting.TroubleshootingSessionService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RouterTurnRunnerTest {
    private static final long SESSION = 1;
    private static final long TICKET = 7;

    private final TroubleshootingSessionService sessions = mock(TroubleshootingSessionService.class);
    private final RouterService router = mock(RouterService.class);
    private final TicketContextLoader tickets = mock(TicketContextLoader.class);
    private final TroubleshootingExecutor executor = new TroubleshootingExecutor();
    private final RouterTurnRunner runner = new RouterTurnRunner(sessions, router, tickets, executor);
    private final AtomicLong logEnd = new AtomicLong(5);

    @AfterEach
    void stop() {
        executor.shutdown();
    }

    @Test
    void inputTheRunningTurnAlreadyReadDoesNotStartAnother() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = stubRouter(release);

        runner.requestTurn(SESSION, TICKET, 5);
        verify(router, timeout(2000)).decide(any(), any());
        runner.requestTurn(SESSION, TICKET, 5);
        release.countDown();
        Thread.sleep(300);
        assertThat(calls).hasValue(1);
    }

    @Test
    void inputArrivingDuringATurnGetsOneMoreTurn() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = stubRouter(release);

        runner.requestTurn(SESSION, TICKET, 5);
        verify(router, timeout(2000)).decide(any(), any());
        logEnd.set(8);
        runner.requestTurn(SESSION, TICKET, 7);
        runner.requestTurn(SESSION, TICKET, 8);
        release.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (calls.get() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(300);
        assertThat(calls).hasValue(2);
    }

    private AtomicInteger stubRouter(CountDownLatch release) {
        AtomicInteger calls = new AtomicInteger();
        when(sessions.append(anyLong(), eq(TroubleshootingEventType.ROUTER_STATUS), any(), any(RouterStatus.class)))
                .thenAnswer(invocation -> event(logEnd.get()));
        when(sessions.events(SESSION)).thenAnswer(invocation -> ImmutableList.of(event(logEnd.get())));
        when(router.decide(any(), any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            release.await(2, TimeUnit.SECONDS);
            return new RouterResult(new RouterDecision(RouterAction.DISCUSS, "ok", "r"), new RouterUsage("m", 1, 1, 1));
        });
        return calls;
    }

    private static TroubleshootingEvent event(long seq) {
        return new TroubleshootingEvent(
                SESSION,
                seq,
                TroubleshootingEventType.ROUTER_STATUS,
                "router",
                JsonNodeFactory.instance.objectNode(),
                Instant.now());
    }
}
