package com.coreeng.supportbot.config;

import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.App;
import com.slack.api.model.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Configuration
@Slf4j
@RequiredArgsConstructor
@Profile("!test")
public class SlackAppInit implements InitializingBean {
    private final App app;
    private final ExecutorService executor;
    private final List<SlackEventHandler<? extends Event>> eventHandlers;
    private final List<SlackBlockActionHandler> actionHandlers;
    private final List<SlackViewSubmitHandler> submitHandlers;


    @Override
    public void afterPropertiesSet() {
        eventHandlers.stream().collect(groupingBy(SlackEventHandler::getEventClass))
            .forEach((eventClass, handlers) -> {
                app.event(eventClass, (event, ctx) -> {
                    for (var handler : handlers) {
                        executor.submit(() -> {
                            try {
                                handler.applyUntyped(event, ctx);
                            } catch (Exception e) {
                                log.atError()
                                    .setCause(e)
                                    .addArgument(event::getEnterpriseId)
                                    .addArgument(event::getType)
                                    .addArgument(event::getEvent)
                                    .log("Error while handling event(id: {}, type: {}, body: {})");
                            }
                        });
                    }
                    return ctx.ack();
                });
            });

        for (var handler : actionHandlers) {
            app.blockAction(handler.getPattern(), (req, ctx) -> {
                executor.submit(() -> {
                    try {
                        handler.apply(req, ctx);
                    } catch (Exception e) {
                        log.atError()
                            .setCause(e)
                            .addArgument(() ->
                                req.getPayload().getActions().stream()
                                    .map(BlockActionPayload.Action::getActionId)
                                    .collect(toList())
                            )
                            .addArgument(() -> req.getPayload().getChannel() != null
                                ? req.getPayload().getChannel().getId()
                                : null)
                            .addArgument(() -> req.getPayload().getMessage() != null
                                ? req.getPayload().getMessage().getTs()
                                : null)
                            .addArgument(() -> req.getPayload().getMessage() != null
                                ? req.getPayload().getMessage().getThreadTs()
                                : null)
                            .log("Error while handling blockAction(ids: {}, channel: {}, messageTs: {}, threadTs: {})");
                    }
                });
                return ctx.ack();
            });
        }

        for (var handler : submitHandlers) {
            app.viewSubmission(handler.getPattern(), (req, ctx) -> {
                // Have to be processed synchronously
                return ctx.ack(handler.apply(req, ctx));
            });
        }
    }
}
