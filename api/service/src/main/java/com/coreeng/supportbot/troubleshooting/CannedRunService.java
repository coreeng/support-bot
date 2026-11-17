package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.troubleshooting.Payloads.RunUpdate;
import com.coreeng.supportbot.troubleshooting.router.RouterAction;
import com.coreeng.supportbot.troubleshooting.router.RouterTurnRunner;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stands in for workers and Slack in the spike. An approved reply or close is recorded as "would",
 * never carried out; an approved investigation plays three canned updates and hands back to the
 * router, as the end of a real run would.
 */
@Component
@ConditionalOnTroubleshootingEnabled
@Slf4j
public class CannedRunService {
    private static final Duration STEP = Duration.ofSeconds(1);

    private final TroubleshootingSessionService sessions;
    private final RouterTurnRunner turns;
    private final TroubleshootingExecutor executor;

    public CannedRunService(
            TroubleshootingSessionService sessions, RouterTurnRunner turns, TroubleshootingExecutor executor) {
        this.sessions = sessions;
        this.turns = turns;
        this.executor = executor;
    }

    public void approved(long sessionId, long ticketId, RouterAction action, String text) {
        switch (action) {
            case REPLY -> update(sessionId, "WOULD_POST", "Would post to the Slack thread (not posted):\n" + text);
            case CLOSE -> update(sessionId, "WOULD_CLOSE", "Would close the ticket (not closed): " + text);
            case INVESTIGATE -> executor.execute(() -> investigate(sessionId, ticketId, text));
            case DISCUSS -> {}
        }
    }

    private void investigate(long sessionId, long ticketId, String brief) {
        List<RunUpdate> steps = List.of(
                new RunUpdate("STARTED", "Canned run started for: " + brief),
                new RunUpdate("OUTPUT", """
                        $ kubectl get events -n tenant-app --field-selector type=Warning
                        LAST SEEN   TYPE      REASON             OBJECT                MESSAGE
                        2m          Warning   FailedScheduling   pod/api-7d9f8-x2k4q   0/6 nodes are available: 6 Insufficient cpu.
                        (canned output, no cluster was queried)"""),
                new RunUpdate(
                        "NOTE",
                        "Case note (canned): pods are Pending because the node pool has no spare CPU. Ruled out: image"
                                + " pull errors, quota on the namespace. Next step: raise the node pool limit or lower"
                                + " requests."));
        try {
            long lastSeq = 0;
            for (RunUpdate step : steps) {
                Thread.sleep(STEP);
                lastSeq = sessions.append(sessionId, TroubleshootingEventType.RUN_UPDATE, Actors.SYSTEM, step)
                        .seq();
            }
            turns.requestTurn(sessionId, ticketId, lastSeq);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).addArgument(sessionId).log("Canned run failed for session {}");
        }
    }

    private void update(long sessionId, String kind, String text) {
        sessions.append(sessionId, TroubleshootingEventType.RUN_UPDATE, Actors.SYSTEM, new RunUpdate(kind, text));
    }
}
