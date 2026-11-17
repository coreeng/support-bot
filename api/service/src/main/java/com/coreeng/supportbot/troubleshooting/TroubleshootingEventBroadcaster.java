package com.coreeng.supportbot.troubleshooting;

import com.google.common.collect.ImmutableList;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans session events out to open streams. Every flush reads "events after the last one this
 * stream sent" from the database, so the replay on connect and live updates are the same code and
 * a stream never skips or repeats an event. Streams live in memory: this works with one API
 * replica only.
 */
@Component
@ConditionalOnTroubleshootingEnabled
@Slf4j
public class TroubleshootingEventBroadcaster {
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final TroubleshootingRepository repository;
    private final TroubleshootingExecutor executor;
    private final Map<Long, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public TroubleshootingEventBroadcaster(TroubleshootingRepository repository, TroubleshootingExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public SseEmitter subscribe(long sessionId, long afterSeq, Function<TroubleshootingEvent, Object> toWire) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        Subscriber subscriber = new Subscriber(sessionId, emitter, toWire, afterSeq);
        subscribers
                .computeIfAbsent(sessionId, id -> ConcurrentHashMap.newKeySet())
                .add(subscriber);
        emitter.onCompletion(() -> remove(subscriber));
        emitter.onTimeout(() -> remove(subscriber));
        emitter.onError(error -> remove(subscriber));
        submitFlush(subscriber);
        return emitter;
    }

    public void eventsAppended(long sessionId) {
        for (Subscriber subscriber : subscribers.getOrDefault(sessionId, Set.of())) {
            submitFlush(subscriber);
        }
    }

    public int openStreams() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    @Scheduled(fixedDelay = 15_000)
    public void keepAlive() {
        for (Set<Subscriber> sessionSubscribers : subscribers.values()) {
            for (Subscriber subscriber : sessionSubscribers) {
                subscriber.send(SseEmitter.event().comment("keep-alive"));
            }
        }
    }

    @PreDestroy
    public void closeAll() {
        subscribers.values().forEach(set -> set.forEach(subscriber -> subscriber.emitter.complete()));
        subscribers.clear();
    }

    private void submitFlush(Subscriber subscriber) {
        executor.execute(subscriber::flush);
    }

    private void remove(Subscriber subscriber) {
        Set<Subscriber> sessionSubscribers = subscribers.get(subscriber.sessionId);
        if (sessionSubscribers != null) {
            sessionSubscribers.remove(subscriber);
        }
    }

    private final class Subscriber {
        private final long sessionId;
        private final SseEmitter emitter;
        private final Function<TroubleshootingEvent, Object> toWire;
        private final ReentrantLock lock = new ReentrantLock();
        private long lastSent;

        Subscriber(long sessionId, SseEmitter emitter, Function<TroubleshootingEvent, Object> toWire, long lastSent) {
            this.sessionId = sessionId;
            this.emitter = emitter;
            this.toWire = toWire;
            this.lastSent = lastSent;
        }

        void flush() {
            lock.lock();
            try {
                ImmutableList<TroubleshootingEvent> events = repository.findEventsAfter(sessionId, lastSent);
                for (TroubleshootingEvent event : events) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.seq()))
                            .data(toWire.apply(event), MediaType.APPLICATION_JSON));
                    lastSent = event.seq();
                }
            } catch (IOException | IllegalStateException e) {
                log.atDebug().addArgument(sessionId).log("Stream for session {} closed while sending");
                remove(this);
                emitter.completeWithError(e);
            } catch (RuntimeException e) {
                log.atWarn().setCause(e).addArgument(sessionId).log("Failed to flush stream for session {}");
            } finally {
                lock.unlock();
            }
        }

        void send(SseEmitter.SseEventBuilder event) {
            if (!lock.tryLock()) {
                return;
            }
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                remove(this);
                emitter.completeWithError(e);
            } finally {
                lock.unlock();
            }
        }
    }
}
