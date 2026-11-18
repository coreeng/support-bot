package com.coreeng.supportbot.troubleshooting;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/**
 * Runs router turns, the Slack thread snapshot and stream flushes on virtual threads. Not the
 * analysis executor: it has one thread and no queue, so a turn would be rejected while a summary or
 * analysis run holds it. Wrapped in its own type rather than exposed as an {@code ExecutorService}
 * bean, which would make other unqualified executor injections ambiguous.
 */
@Component
@ConditionalOnTroubleshootingEnabled
public class TroubleshootingExecutor {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @SuppressWarnings("FutureReturnValueIgnored")
    public void execute(Runnable task) {
        executor.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
