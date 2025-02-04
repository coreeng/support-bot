package com.coreeng.supportbot.mock;

import com.coreeng.supportbot.config.SlackEscalationProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationRepository;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.sentiment.SentimentRepository;
import com.coreeng.supportbot.sentiment.TicketSentimentResults;
import com.coreeng.supportbot.sentiment.client.Sentiment;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.round;

@Component
@ConditionalOnProperty("mock-data.enabled")
@RequiredArgsConstructor
@Slf4j
public class MockDataGenerator implements ApplicationRunner {
    private final static long secondsPerDay = 24 * 60 * 60;
    private final static double avgTicketsPerDay = 35;
    private final static double stdTicketsPerDay = 10;
    private final static int maxTicketsPerDay = 50;
    private final static double avgQueryResponseTimeSecs = 10 * 60;
    private final static double stdQueryResponseTimeSecs = 5 * 60;
    private final static double avgTicketReopenedTimes = 0.5;
    private final static double stdTicketReopenedTimes = 2.0;
    private final static long ticketChangeStatusDelayLowerBoundSecs = 60 * 60; //NOPMD - suppressed LongVariable
    private final static long ticketChangeStatusDelayHigherBoundSecs = 3 * 60 * 60; //NOPMD - suppressed LongVariable
    private final static long maxNumberOfEscalations = 3;
    private final static double avgTicketEscalatedAfterSeconds = 30 * 60; //NOPMD - suppressed LongVariable
    private final static double stdTicketEscalatedAfterSeconds = 5 * 60; //NOPMD - suppressed LongVariable
    private final static double avgEscalationResolutionTime = 10 * 60; //NOPMD - suppressed LongVariable
    private final static double stdEscalationResolutionTime = 2 * 60; //NOPMD - suppressed LongVariable
    private final static double closedTicketIsEscalatedChance = 33.0; //NOPMD - suppressed LongVariable

    private final SlackTicketsProps ticketsProps;
    private final SlackEscalationProps escalationProps;
    private final TicketRepository ticketRepository;
    private final EscalationRepository escalationRepository;
    private final SentimentRepository sentimentRepository;
    private final PlatformTeamsService platformTeamsService;
    private final ImpactsRegistry impactsRegistry;
    private final TagsRegistry tagsRegistry;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final ZoneId timezone;

    @Override
    public void run(ApplicationArguments args) {
        Random random = new Random();
        LocalDate nowDate = LocalDate.now();
        LocalDate date = nowDate.minusWeeks(2);

        int ticketsGeneratedForDate = 0;
        int ticketsToGenerateForDate = getNextTicketsToGenerateForDate(random, date);
        long totalTicketsGenerated = 0;
        while (date.isBefore(nowDate)) {
            TicketProgression progression = TicketProgression.pick(random);
            switch (progression) {
                case queried -> generateQuery(random, date);
                case created -> generateCreatedTicket(random, date);
                case filledWithDetails -> generateFilledTicket(random, date);
                case escalated -> generateEscalatedTicket(random, date);
                case closed -> generateClosedTicket(random, date);
            }
            ticketsGeneratedForDate += 1;
            totalTicketsGenerated += 1;

            if (ticketsGeneratedForDate >= ticketsToGenerateForDate) {
                ticketsGeneratedForDate = 0;
                ticketsToGenerateForDate = getNextTicketsToGenerateForDate(random, date);
                date = date.plusDays(1);
            }
        }
        log.atInfo()
            .addArgument(totalTicketsGenerated)
            .log("Total tickets generated: {}");
    }

    private int getNextTicketsToGenerateForDate(Random random, LocalDate date) {
        int dayOfWeek = date.get(ChronoField.DAY_OF_WEEK);
        if (dayOfWeek == 6 || dayOfWeek == 7) {
            return 0;
        }
        return Math.clamp(
            round(random.nextGaussian(avgTicketsPerDay, stdTicketsPerDay)),
            0,
            maxTicketsPerDay
        );
    }

    private void generateQuery(Random random, LocalDate date) {
        MessageTs queryTs = generateMessageTsAt(random, date);
        ticketRepository.createQueryIfNotExists(queryTs);
    }

    private Ticket generateCreatedTicket(Random random, LocalDate date) {
        MessageTs queryTs = generateMessageTsAt(random, date);
        MessageTs createdMessageTs = generateMessageTsAfter(random, queryTs.getDate(), avgQueryResponseTimeSecs, stdQueryResponseTimeSecs);
        return ticketRepository.createTicketIfNotExists(
            Ticket.createNew(queryTs, ticketsProps.channelId()).toBuilder()
                .createdMessageTs(createdMessageTs)
                .statusHistory(ImmutableList.of(new Ticket.StatusLog(
                    TicketStatus.opened,
                    createdMessageTs.getDate()
                )))
                .build()
        );
    }

    private Ticket generateFilledTicket(Random random, LocalDate date) {
        Ticket ticket = generateCreatedTicket(random, date);

        ImmutableList<PlatformTeam> teams = platformTeamsService.listTeams();
        int teamsI = random.nextInt(teams.size());

        Set<Tag> pickedTags = generatePickedTags(random);

        ImmutableList<TicketImpact> impacts = impactsRegistry.listAllImpacts();
        int impactI = random.nextInt(0, impacts.size());

        long reopenedTimes = round(max(0.0, random.nextGaussian(avgTicketReopenedTimes, stdTicketReopenedTimes)));
        ImmutableList.Builder<Ticket.StatusLog> statusHistory = ImmutableList.<Ticket.StatusLog>builder()
            .addAll(ticket.statusHistory());
        Instant nextStatusChangeDate = ticket.statusHistory().getLast().timestamp();
        for (int i = 0; i < reopenedTimes; i++) {
            long delaySecs = random.nextLong(ticketChangeStatusDelayLowerBoundSecs, ticketChangeStatusDelayHigherBoundSecs);
            nextStatusChangeDate = nextStatusChangeDate.plusSeconds(delaySecs);
            Ticket.StatusLog closedLog = new Ticket.StatusLog(
                // assuming that in the beginning, we have a single log about opened ticket
                TicketStatus.closed,
                nextStatusChangeDate
            );
            delaySecs = random.nextLong(ticketChangeStatusDelayLowerBoundSecs, ticketChangeStatusDelayHigherBoundSecs);
            nextStatusChangeDate = nextStatusChangeDate.plusSeconds(delaySecs);
            Ticket.StatusLog openedLog = new Ticket.StatusLog(
                TicketStatus.opened,
                nextStatusChangeDate
            );
            if (nextStatusChangeDate.isAfter(Instant.now())) {
                break;
            }
            statusHistory.add(closedLog);
            statusHistory.add(openedLog);
        }

        Ticket updatedTicket = ticket.toBuilder()
            .team(teams.get(teamsI).name())
            .tags(ImmutableList.copyOf(pickedTags))
            .impact(impacts.get(impactI))
            .statusHistory(statusHistory.build())
            .build();
        return ticketRepository.updateTicket(updatedTicket);
    }

    private Ticket generateEscalatedTicket(Random random, LocalDate date) {
        Ticket ticket = generateFilledTicket(random, date);
        long escalationsAmount = random.nextLong(1, maxNumberOfEscalations + 1);
        for (int i = 0; i < escalationsAmount; i++) {
            generateEscalation(
                random,
                ticket.statusHistory().getFirst().timestamp(),
                ticket
            );
        }
        return ticket;
    }

    private void generateEscalation(Random random,
                                    Instant afterDate,
                                    Ticket ticket) {
        MessageTs escalationTs = generateMessageTsAfter(
            random,
            afterDate,
            avgTicketEscalatedAfterSeconds,
            stdTicketEscalatedAfterSeconds
        );
        MessageTs createdMessageTs = messageTsFromInstant(random, escalationTs.getDate());

        ImmutableList<EscalationTeam> escalationTeams = escalationTeamsRegistry.listAllEscalationTeams();
        int teamI = random.nextInt(0, escalationTeams.size());

        Set<Tag> pickedTags = generatePickedTags(random);

        Escalation escalation = Escalation.createNew(
            ticket.id(),
            new MessageRef(escalationTs, escalationProps.channelId()),
            escalationTeams.get(teamI).name(),
            ImmutableList.copyOf(pickedTags)
        );
        escalation = escalation.toBuilder()
            .openedAt(escalationTs.getDate())
            .createdMessageTs(createdMessageTs)
            .build();

        if (random.nextBoolean()) {
            escalation = makeEscalationResolved(random, escalation);
        }

        escalationRepository.createIfNotExists(escalation);
    }

    private void generateClosedTicket(Random random, LocalDate date) {
        Ticket ticket;
        if (closedTicketIsEscalatedChance <= random.nextDouble(0.0, 100.0)) {
            ticket = generateEscalatedTicket(random, date);
        } else {
            ticket = generateFilledTicket(random, date);
        }

        ImmutableList<Escalation> escalations = escalationRepository.listByTicketId(ticket.id());
        for (Escalation escalation : escalations) {
            if (escalation.status() != EscalationStatus.resolved) {
                Escalation esc = makeEscalationResolved(random, escalation);
                escalationRepository.update(esc);
            }
        }

        long closeDelay = random.nextLong(ticketChangeStatusDelayLowerBoundSecs, ticketChangeStatusDelayHigherBoundSecs);
        Instant closeDate = ticket.statusHistory().getLast().timestamp().plusSeconds(closeDelay);
        ImmutableList<Ticket.StatusLog> statusHistory = ImmutableList.<Ticket.StatusLog>builder()
            .addAll(ticket.statusHistory())
            .add(new Ticket.StatusLog(
                TicketStatus.closed,
                min(closeDate, Instant.now())
            ))
            .build();
        ticket = ticket.toBuilder()
            .status(TicketStatus.closed)
            .statusHistory(statusHistory)
            .build();
        ticketRepository.updateTicket(ticket);

        generateSentimentForTicket(random, ticket);
    }

    private Escalation makeEscalationResolved(Random random, Escalation escalation) {
        long resolutionSecs = round(max(0.0, random.nextGaussian(
            avgEscalationResolutionTime,
            stdEscalationResolutionTime
        )));
        Instant resolvedAt = escalation.openedAt().plusSeconds(resolutionSecs);
        return escalation.toBuilder()
            .status(EscalationStatus.resolved)
            .resolvedAt(resolvedAt)
            .build();
    }

    private Set<Tag> generatePickedTags(Random random) {
        ImmutableList<Tag> tags = tagsRegistry.listAllTags();
        int tagsAmount = random.nextInt(1, 4);
        Set<Tag> pickedTags = new HashSet<>();
        if (tagsAmount == tags.size()) {
            pickedTags.addAll(tags);
        } else {
            while (pickedTags.size() != tagsAmount) {
                int tagI = random.nextInt(0, tags.size());
                pickedTags.add(tags.get(tagI));
            }
        }
        return pickedTags;
    }

    private void generateSentimentForTicket(Random random, Ticket ticket) {
        sentimentRepository.save(ticket.id(),
            TicketSentimentResults.builder()
                .ticketId(ticket.id())
                .authorSentiment(generateSentiment(random))
                .supportSentiment(random.nextDouble() > 0.2
                    ? generateSentiment(random)
                    : null)
                .othersSentiment(random.nextDouble() > 0.5
                    ? generateSentiment(random)
                    : null)
                .build()
        );
    }

    private Sentiment generateSentiment(Random random) {
        double positiveSentiment = random.nextDouble();
        double neutralSentiment = random.nextDouble();
        double negativeSentiment = random.nextDouble();
        double sentimentSum = positiveSentiment + neutralSentiment + negativeSentiment;
        return new Sentiment(
            positiveSentiment / sentimentSum,
            neutralSentiment / sentimentSum,
            negativeSentiment / sentimentSum
        );
    }

    private MessageTs generateMessageTsAt(Random random, LocalDate date) {
        LocalTime time = LocalTime.ofSecondOfDay(random.nextLong(secondsPerDay));
        ZoneOffset offset = Instant.now().atZone(timezone).getOffset();
        Instant queryCreatedDate = date.atTime(time).toInstant(offset);
        return messageTsFromInstant(random, queryCreatedDate);
    }

    private MessageTs generateMessageTsAfter(Random random, Instant date, double avgSeconds, double stdSeconds) {
        long afterSeconds = round(max(0.0, random.nextGaussian(avgSeconds, stdSeconds)));
        Instant tsCreatedDate = date.plusSeconds(afterSeconds);
        return messageTsFromInstant(random, tsCreatedDate);
    }

    private MessageTs messageTsFromInstant(Random random, Instant tsCreatedDate) {
        long epochSecond = tsCreatedDate.getEpochSecond();
        StringBuilder postfixBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            postfixBuilder.append(random.nextInt(10));
        }
        return MessageTs.mocked(epochSecond + "." + postfixBuilder);
    }

    private <T extends Comparable<T>> T min(T a, T b) {
        if (a.compareTo(b) > 0) {
            return b;
        } else {
            return a;
        }
    }

    @Getter
    @RequiredArgsConstructor
    private enum TicketProgression {
        queried(5.0),
        created(10.0),
        filledWithDetails(30.0),
        escalated(20.0),
        closed(35.0);

        static {
            double runningChances = 0.0;
            for (TicketProgression p : values()) {
                runningChances += p.pickChances;
                p.pickBorder = runningChances;
            }
        }

        private final double pickChances;
        private double pickBorder;

        private static TicketProgression pick(Random random) {
            double totalChances = Arrays.stream(values())
                .mapToDouble(TicketProgression::pickChances)
                .sum();
            double r = random.nextDouble(totalChances);
            for (TicketProgression p : values()) {
                if (r <= p.pickBorder()) {
                    return p;
                }
            }
            throw new IllegalStateException("Couldn't pick Progression type: " + r);
        }
    }
}



