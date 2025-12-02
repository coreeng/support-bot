package com.coreeng.supportbot.config;

import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.SlackBlockSuggestionHandler;
import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.App;
import com.slack.api.model.event.Event;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.stream.Collectors.*;

@Configuration
@Slf4j
@Profile("!test")
@RequiredArgsConstructor
public class SlackAppInit implements InitializingBean {
    private static final String typeEvent = "event";
    private static final String typeAction = "action";
    private static final String typeSuggestion = "suggestion";
    private static final String typeViewSubmission = "view_submission";

    private final App app;
    private final ExecutorService executor;
    private final List<SlackEventHandler<? extends Event>> eventHandlers;
    private final List<SlackBlockActionHandler> actionHandlers;
    private final List<SlackViewSubmitHandler> submitHandlers;
    private final List<SlackBlockSuggestionHandler> blockSuggestionHandlers;
    private final MeterRegistry meterRegistry;

    private Counter receivedCounter(String type, String handler) {
        return Counter.builder("slack_notifications_received_total")
            .description("Total number of Slack notifications received")
            .tag("type", type)
            .tag("handler", handler)
            .register(meterRegistry);
    }

    private Counter processedCounter(String type, String handler) {
        return Counter.builder("slack_notifications_processed_total")
            .description("Total number of Slack notifications successfully processed")
            .tag("type", type)
            .tag("handler", handler)
            .register(meterRegistry);
    }

    private Counter errorCounter(String type, String handler, String errorType) {
        return Counter.builder("slack_notifications_errors_total")
            .description("Total number of Slack notification processing errors")
            .tag("type", type)
            .tag("handler", handler)
            .tag("error_type", errorType)
            .register(meterRegistry);
    }

    private Timer durationTimer(String type, String handler) {
        return Timer.builder("slack_notifications_duration_seconds")
            .description("Slack notification processing duration")
            .tag("type", type)
            .tag("handler", handler)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    @Override
    public void afterPropertiesSet() {
        eventHandlers.stream().collect(groupingBy(SlackEventHandler::getEventClass))
            .forEach((eventClass, handlers) -> {
                app.event(eventClass, (event, ctx) -> {
                    String eventType = event.getEvent().getType();
                    receivedCounter(typeEvent, eventType).increment();

                    for (var handler : handlers) {
                        executor.submit(() -> {
                            Timer.Sample sample = Timer.start(meterRegistry);
                            try {
                                handler.applyUntyped(event, ctx);
                                processedCounter(typeEvent, eventType).increment();
                            } catch (Exception e) {
                                errorCounter(typeEvent, eventType, e.getClass().getSimpleName()).increment();
                                log.atError()
                                    .setCause(e)
                                    .addArgument(event::getEnterpriseId)
                                    .addArgument(event::getType)
                                    .addArgument(event::getEvent)
                                    .log("Error while handling event(id: {}, type: {}, body: {})");
                            } finally {
                                sample.stop(durationTimer(typeEvent, eventType));
                            }
                        });
                    }
                    return ctx.ack();
                });
            });

        for (var handler : actionHandlers) {
            app.blockAction(handler.getPattern(), (req, ctx) -> {
                // Looks like slack only sends one action at a time, but in case it changes in the future, we'll see it
                String actionIdMetricLabel = req.getPayload().getActions().stream()
                    .map(BlockActionPayload.Action::getActionId)
                    .collect(joining("|"));
                receivedCounter(typeAction, actionIdMetricLabel).increment();

                executor.submit(() -> {
                    Timer.Sample sample = Timer.start(meterRegistry);
                    try {
                        handler.apply(req, ctx);
                        processedCounter(typeAction, actionIdMetricLabel).increment();
                    } catch (Exception e) {
                        errorCounter(typeAction, actionIdMetricLabel, e.getClass().getSimpleName()).increment();
                        log.atError()
                            .setCause(e)
                            .addArgument(() -> req.getPayload().getActions().stream()
                                .map(BlockActionPayload.Action::getActionId)
                                .collect(toList()))
                            .addArgument(() -> req.getPayload().getChannel() != null
                                ? req.getPayload().getChannel().getId() : null)
                            .addArgument(() -> req.getPayload().getMessage() != null
                                ? req.getPayload().getMessage().getTs() : null)
                            .addArgument(() -> req.getPayload().getMessage() != null
                                ? req.getPayload().getMessage().getThreadTs() : null)
                            .log("Error while handling blockAction(ids: {}, channel: {}, messageTs: {}, threadTs: {})");
                    } finally {
                        sample.stop(durationTimer(typeAction, actionIdMetricLabel));
                    }
                });
                return ctx.ack();
            });
        }

        for (var handler : blockSuggestionHandlers) {
            app.blockSuggestion(handler.getPattern(), (req, ctx) -> {
                String actionId = req.getPayload().getActionId();
                receivedCounter(typeSuggestion, actionId).increment();

                Timer.Sample sample = Timer.start(meterRegistry);
                try {
                    var result = ctx.ack(handler.apply(req, ctx));
                    processedCounter(typeSuggestion, actionId).increment();
                    return result;
                } catch (Exception e) {
                    errorCounter(typeSuggestion, actionId, e.getClass().getSimpleName()).increment();
                    throw e;
                } finally {
                    sample.stop(durationTimer(typeSuggestion, actionId));
                }
            });
        }

        for (var handler : submitHandlers) {
            app.viewSubmission(handler.getPattern(), (req, ctx) -> {
                String callbackId = req.getPayload().getView().getCallbackId();
                receivedCounter(typeViewSubmission, callbackId).increment();

                Timer.Sample sample = Timer.start(meterRegistry);
                try {
                    var result = ctx.ack(handler.apply(req, ctx));
                    processedCounter(typeViewSubmission, callbackId).increment();
                    return result;
                } catch (Exception e) {
                    errorCounter(typeViewSubmission, callbackId, e.getClass().getSimpleName()).increment();
                    throw e;
                } finally {
                    sample.stop(durationTimer(typeViewSubmission, callbackId));
                }
            });
        }
    }
}
