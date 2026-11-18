package com.coreeng.supportbot.troubleshooting.router;

import com.coreeng.supportbot.troubleshooting.Actors;
import com.coreeng.supportbot.troubleshooting.ConditionalOnTroubleshootingEnabled;
import com.coreeng.supportbot.troubleshooting.Payloads;
import com.coreeng.supportbot.troubleshooting.Payloads.RouterState;
import com.coreeng.supportbot.troubleshooting.Payloads.RouterStatus;
import com.coreeng.supportbot.troubleshooting.TroubleshootingEvent;
import com.coreeng.supportbot.troubleshooting.TroubleshootingEventType;
import com.coreeng.supportbot.troubleshooting.TroubleshootingExecutor;
import com.coreeng.supportbot.troubleshooting.TroubleshootingSessionService;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs at most one router turn per session at a time. Input that arrives during a turn is already in
 * the log; it causes one more turn when the current one ends, unless that turn had already read it. The guard is in
 * memory: one API replica only, and a restart forgets a pending turn.
 */
@Component
@ConditionalOnTroubleshootingEnabled
@Slf4j
public class RouterTurnRunner {
    private final TroubleshootingSessionService sessions;
    private final RouterService router;
    private final TicketContextLoader ticketContextLoader;
    private final TroubleshootingExecutor executor;
    private final Map<Long, TurnState> turns = new ConcurrentHashMap<>();

    public RouterTurnRunner(
            TroubleshootingSessionService sessions,
            RouterService router,
            TicketContextLoader ticketContextLoader,
            TroubleshootingExecutor executor) {
        this.sessions = sessions;
        this.router = router;
        this.ticketContextLoader = ticketContextLoader;
        this.executor = executor;
    }

    /**
     * @param upToSeq the event that asks for the turn; a running turn that already read it does not
     *     need another one
     */
    public void requestTurn(long sessionId, long ticketId, long upToSeq) {
        TurnState state = turns.computeIfAbsent(sessionId, id -> new TurnState());
        synchronized (state) {
            state.requestedUpTo = Math.max(state.requestedUpTo, upToSeq);
            if (state.running) {
                return;
            }
            state.running = true;
        }
        executor.execute(() -> runUntilSettled(sessionId, ticketId, state));
    }

    private void runUntilSettled(long sessionId, long ticketId, TurnState state) {
        while (true) {
            long readUpTo = runTurn(sessionId, ticketId);
            synchronized (state) {
                if (state.requestedUpTo <= readUpTo) {
                    state.running = false;
                    return;
                }
            }
        }
    }

    /** Returns the last sequence number the turn read. */
    long runTurn(long sessionId, long ticketId) {
        long readUpTo = sessions.append(
                        sessionId,
                        TroubleshootingEventType.ROUTER_STATUS,
                        Actors.ROUTER,
                        new RouterStatus(RouterState.THINKING, null))
                .seq();
        try {
            ImmutableList<TroubleshootingEvent> events = sessions.events(sessionId);
            readUpTo = events.isEmpty() ? readUpTo : events.getLast().seq();
            RouterResult result = router.decide(ticketContextLoader.load(ticketId), events);
            RouterDecision decision = result.decision();
            RouterUsage usage = result.usage();
            sessions.append(
                    sessionId,
                    TroubleshootingEventType.ROUTER_MESSAGE,
                    Actors.ROUTER,
                    new Payloads.RouterMessage(
                            decision.text(),
                            usage.model(),
                            usage.inputTokens(),
                            usage.outputTokens(),
                            usage.durationMs()));
            sessions.append(
                    sessionId,
                    TroubleshootingEventType.ROUTER_STATUS,
                    Actors.ROUTER,
                    new RouterStatus(RouterState.IDLE, null));
        } catch (RouterFailureException e) {
            fail(sessionId, Objects.requireNonNullElse(e.getMessage(), "the router failed"));
        } catch (RuntimeException e) {
            log.atError().setCause(e).addArgument(sessionId).log("Router turn crashed for session {}");
            fail(sessionId, "an internal error occurred");
        }
        return readUpTo;
    }

    private void fail(long sessionId, String reason) {
        sessions.append(
                sessionId,
                TroubleshootingEventType.ROUTER_STATUS,
                Actors.ROUTER,
                new RouterStatus(RouterState.FAILED, reason));
    }

    private static final class TurnState {
        private boolean running;
        private long requestedUpTo;
    }
}
