package com.coreeng.supportbot.mock;

import static java.lang.Math.max;
import static java.lang.Math.round;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
import com.coreeng.supportbot.ticket.TicketTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty("mock-data.enabled")
@RequiredArgsConstructor
@Slf4j
@Order(200)
public class MockDataGenerator implements ApplicationRunner {
    private static final long secondsPerDay = 24 * 60 * 60;
    private static final double avgTicketsPerDay = 35;
    private static final double stdTicketsPerDay = 10;
    private static final int maxTicketsPerDay = 50;
    private static final double avgQueryResponseTimeSecs = 10 * 60;
    private static final double stdQueryResponseTimeSecs = 5 * 60;
    private static final double avgTicketReopenedTimes = 0.5;
    private static final double stdTicketReopenedTimes = 2.0;
    private static final long ticketChangeStatusDelayLowerBoundSecs = 60 * 60; //NOPMD - suppressed LongVariable
    private static final long ticketChangeStatusDelayHigherBoundSecs = 3 * 60 * 60; //NOPMD - suppressed LongVariable
    private static final long maxNumberOfEscalations = 3;
    private static final double avgTicketEscalatedAfterSeconds = 30 * 60; //NOPMD - suppressed LongVariable
    private static final double stdTicketEscalatedAfterSeconds = 5 * 60; //NOPMD - suppressed LongVariable
    private static final double avgEscalationResolutionTime = 10 * 60; //NOPMD - suppressed LongVariable
    private static final double stdEscalationResolutionTime = 2 * 60; //NOPMD - suppressed LongVariable
    private static final double closedTicketIsEscalatedChance = 33.0; //NOPMD - suppressed LongVariable

    private final SlackTicketsProps ticketsProps;
    private final TicketRepository ticketRepository;
    private final EscalationRepository escalationRepository;
    @Nullable
    private final SentimentRepository sentimentRepository;
    private final PlatformTeamsService platformTeamsService;
    private final ImpactsRegistry impactsRegistry;
    private final TagsRegistry tagsRegistry;
    private final EscalationTeamsRegistry escalationTeamsRegistry;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        Page<Ticket> existingTickets = ticketRepository.listTickets(TicketsQuery.builder()
            .build());
        if (existingTickets.totalElements() > 0) {
            log.atWarn()
                .addArgument(existingTickets::totalElements)
                .log("Skipping mock data generation because there are already existing tickets: {}");
            return;
        }

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
        ticketRepository.createQueryIfNotExists(new MessageRef(queryTs, ticketsProps.channelId()));
    }

    private Ticket generateCreatedTicket(Random random, LocalDate date) {
        MessageTs queryTs = generateMessageTsAt(random, date);
        MessageTs createdMessageTs = generateMessageTsAfter(random, queryTs.getDate(), avgQueryResponseTimeSecs, stdQueryResponseTimeSecs);
        return ticketRepository.createTicketIfNotExists(
            Ticket.createNew(queryTs, ticketsProps.channelId()).toBuilder()
                .createdMessageTs(createdMessageTs)
                .statusLog(ImmutableList.of(new Ticket.StatusLog(
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
        Instant nextStatusChangeDate = ticket.statusLog().getLast().date();
        for (int i = 0; i < reopenedTimes; i++) {
            long delaySecs = random.nextLong(ticketChangeStatusDelayLowerBoundSecs, ticketChangeStatusDelayHigherBoundSecs);
            nextStatusChangeDate = nextStatusChangeDate.plusSeconds(delaySecs);
            Instant closedLogAt = nextStatusChangeDate;
            delaySecs = random.nextLong(ticketChangeStatusDelayLowerBoundSecs, ticketChangeStatusDelayHigherBoundSecs);
            nextStatusChangeDate = nextStatusChangeDate.plusSeconds(delaySecs);
            Instant openedLogAt = nextStatusChangeDate;
            if (nextStatusChangeDate.isAfter(Instant.now())) {
                break;
            }

            ticket = ticketRepository.insertStatusLog(
                ticket.toBuilder()
                    .status(TicketStatus.closed)
                    .build(),
                closedLogAt
            );
            ticket = ticketRepository.insertStatusLog(
                ticket.toBuilder()
                    .status(TicketStatus.opened)
                    .build(),
                openedLogAt
            );
        }

        Ticket updatedTicket = ticket.toBuilder()
            .team(TicketTeam.fromCode(teams.get(teamsI).name()))
            .tags(
                pickedTags.stream()
                    .map(Tag::code)
                    .collect(toImmutableList())
            )
            .impact(impacts.get(impactI).code())
            .lastInteractedAt(ticket.statusLog().getLast().date())
            .build();
        return ticketRepository.updateTicket(updatedTicket);
    }

    private Ticket generateEscalatedTicket(Random random, LocalDate date) {
        Ticket ticket = generateFilledTicket(random, date);
        long escalationsAmount = random.nextLong(1, maxNumberOfEscalations + 1);
        ImmutableList<EscalationTeam> escalationTeams = escalationTeamsRegistry.listAllEscalationTeams();
        Set<EscalationTeam> escalationTeamsToUse = new HashSet<>();
        while (escalationTeamsToUse.size() < escalationsAmount) {
            escalationTeamsToUse.add(escalationTeams.get(random.nextInt(0, escalationTeams.size())));
        }
        for (EscalationTeam escalationTeam: escalationTeamsToUse) {
            generateEscalation(
                random,
                ticket.statusLog().getFirst().date(),
                ticket,
                escalationTeam
            );
        }
        return ticket;
    }

    private void generateEscalation(Random random,
                                    Instant afterDate,
                                    Ticket ticket,
                                    EscalationTeam escalationTeam) {
        MessageTs escalationTs = generateMessageTsAfter(
            random,
            afterDate,
            avgTicketEscalatedAfterSeconds,
            stdTicketEscalatedAfterSeconds
        );
        MessageTs createdMessageTs = messageTsFromInstant(random, escalationTs.getDate());


        Set<Tag> pickedTags = generatePickedTags(random);

        Escalation escalation = Escalation.createNew(
            checkNotNull(ticket.id()),
            escalationTeam.code(),
            pickedTags.stream()
                .map(Tag::code)
                .collect(toImmutableList()),
            ticket.queryRef()
        );
        escalation = escalation.toBuilder()
            .openedAt(escalationTs.getDate())
            .createdMessageTs(createdMessageTs)
            .build();
        escalation = checkNotNull(escalationRepository.createIfNotExists(escalation));

        if (random.nextBoolean()) {
            Instant resolvedAt = getEscalationResolutionTime(random, escalation);
            escalationRepository.markResolved(escalation, resolvedAt);
        }
    }

    private void generateClosedTicket(Random random, LocalDate date) {
        Ticket ticket;
        if (closedTicketIsEscalatedChance <= random.nextDouble(0.0, 100.0)) {
            ticket = generateEscalatedTicket(random, date);
        } else {
            ticket = generateFilledTicket(random, date);
        }

        ImmutableList<Escalation> escalations = escalationRepository.listByTicketId(checkNotNull(ticket.id()));
        for (Escalation escalation : escalations) {
            if (escalation.status() != EscalationStatus.resolved) {
                Instant resolvedAt = getEscalationResolutionTime(random, escalation);
                escalationRepository.markResolved(escalation, resolvedAt);
            }
        }

        long closeDelay = random.nextLong(ticketChangeStatusDelayLowerBoundSecs, ticketChangeStatusDelayHigherBoundSecs);
        Instant closeDate = ticket.statusLog().getLast().date().plusSeconds(closeDelay);
        ticket = ticketRepository.updateTicket(
            ticket.toBuilder()
                .status(TicketStatus.closed)
                .lastInteractedAt(closeDate)
                .build()
        );
        ticket = ticketRepository.insertStatusLog(
            ticket,
            min(closeDate, Instant.now())
        );

        generateSentimentForTicket(random, ticket);
    }

    private Instant getEscalationResolutionTime(Random random, Escalation escalation) {
        long resolutionSecs = round(max(0.0, random.nextGaussian(
            avgEscalationResolutionTime,
            stdEscalationResolutionTime
        )));
        return escalation.openedAt().plusSeconds(resolutionSecs);
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
        if (sentimentRepository == null) {
            return;
        }
        sentimentRepository.save(checkNotNull(ticket.id()),
            TicketSentimentResults.builder()
                .ticketId(checkNotNull(ticket.id()))
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
        ZoneOffset offset = Instant.now().atZone(ZoneOffset.UTC).getOffset();
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

