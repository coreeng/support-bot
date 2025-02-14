package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@EnableConfigurationProperties(CheckStaleTicketsJob.Params.class)
@Slf4j
@RequiredArgsConstructor
public class CheckStaleTicketsJob {
    private final Params params;
    private final TicketRepository repository;
    private final TicketProcessingService processingService;

    @Scheduled(cron = "${ticket.staleness-check-job.find-stale-cron}")
    public void checkStaleTickets() {
        log.info("Searching for stale tickets");
        ImmutableList<TicketId> staleTicketIds = repository.listStaleTicketIds(Instant.now(), params.timeToStale());
        for (TicketId ticketId : staleTicketIds) {
            try {
                processingService.markAsStale(ticketId);
            } catch (Exception e) {
                log.atError()
                    .addArgument(ticketId)
                    .setCause(e)
                    .log("Error while marking ticket({}) as stale");
            }
        }
    }

    @Scheduled(cron = "${ticket.staleness-check-job.remind-about-stale-cron}")
    public void remindAboutStaleTickets() {
        log.info("Reminding about stale tickets");
        ImmutableList<TicketId> ticketIdsToRemindOf = repository.listStaleTicketIdsToRemindOf(Instant.now(), params.staleReminderInterval());
        for (TicketId ticketId : ticketIdsToRemindOf) {
            try {
                processingService.remindOfStaleTicket(ticketId);
            } catch (Exception e) {
                log.atError()
                    .addArgument(ticketId)
                    .setCause(e)
                    .log("Error while reminding of stale ticket({})");
            }
        }
    }

    @ConfigurationProperties("ticket.staleness-check-job")
    public record Params(
        Duration timeToStale,
        Duration staleReminderInterval
    ) {
    }
}
