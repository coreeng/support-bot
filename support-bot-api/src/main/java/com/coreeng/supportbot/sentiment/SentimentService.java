package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.sentiment.client.Message;
import com.coreeng.supportbot.sentiment.client.Messages;
import com.coreeng.supportbot.sentiment.client.Sentiment;
import com.coreeng.supportbot.sentiment.client.SentimentAIClient;
import com.coreeng.supportbot.sentiment.client.SentimentResponse;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.Objects;

import static com.coreeng.supportbot.ticket.TicketStatus.closed;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

@Service
@RequiredArgsConstructor
public class SentimentService {
    private final TicketQueryService ticketQueryService;
    private final SupportTeamService supportTeamService;
    private final SlackClient slackClient;
    private final SentimentAIClient client;

    public TicketSentimentResults calculateSentiment(TicketId id) {
        Ticket ticket = ticketQueryService.findById(id);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket is not found: " + id.render());
        }
        if (ticket.status() != closed) {
            throw new IllegalArgumentException("Ticket is not closed: " + id.render());
        }

        ConversationsRepliesResponse threadPage = slackClient.getThreadPage(ConversationsRepliesRequest.builder()
            .ts(ticket.queryTs().ts())
            .channel(ticket.channelId())
            .build());
        record UserIdToEmail(String userId, String email) {
        }
        ImmutableMap<String, String> userIdToEmail = threadPage.getMessages().stream()
            .map(com.slack.api.model.Message::getUser)
            .distinct()
            .map(userId -> {
                User.Profile profile = slackClient.getUserById(userId);
                if (profile == null
                    || profile.getEmail() == null
                    || profile.getBotId() != null) {
                    return null;
                }
                return new UserIdToEmail(userId, profile.getEmail());
            })
            .filter(Objects::nonNull)
            .collect(toImmutableMap(
                UserIdToEmail::userId,
                UserIdToEmail::email
            ));

        Team supportTeam = supportTeamService.getTeam();
        ImmutableList<Message> messages = threadPage.getMessages().stream()
            .filter(m -> m.getBotId() == null)
            .map(m -> {
                String email = userIdToEmail.get(m.getUser());
                String team = supportTeamService.isMemberBeUserEmail(email)
                    ? supportTeam.name()
                    : ticket.team();
                return Message.builder()
                    .user(m.getUser())
                    .team(team)
                    .type(m.getType())
                    .threadTs(m.getThreadTs())
                    .ts(m.getTs())
                    .text(m.getText())
                    .build();
            })
            .collect(toImmutableList());

        ImmutableList<SentimentResponse> messageSentiments = client.classifyBulk(new Messages(messages));

        String ticketAuthorId = threadPage.getMessages().getFirst().getUser();
        Sentiment authorSentiment = calculateAuthorSentiment(ticketAuthorId, messageSentiments);
        Sentiment supportSentiment = calculateSupportTeamSentiment(supportTeam.name(), messageSentiments);
        Sentiment othersSentiment = calculateOthersSentiment(ticketAuthorId, supportTeam.name(), messageSentiments);

        return TicketSentimentResults.builder()
            .ticketId(id)
            .authorSentiment(authorSentiment)
            .supportSentiment(supportSentiment)
            .othersSentiment(othersSentiment)
            .build();
    }

    @Nullable
    private Sentiment calculateAuthorSentiment(String ticketAuthorId, ImmutableList<SentimentResponse> messageSentiments) {
        ImmutableList<Sentiment> sentiments = messageSentiments.stream()
            .filter(m -> m.message().user().equals(ticketAuthorId))
            .map(SentimentResponse::sentiment)
            .collect(toImmutableList());

        if (sentiments.isEmpty()) {
            return null;
        }

        final double weight = 0.7;
        double positiveEMA = 0.0;
        double neutralEMA = 0.0;
        double negativeEMA = 0.0;
        for (Sentiment sentiment : sentiments) {
            positiveEMA = sentiment.positive() * weight + positiveEMA * (1.0 - weight);
            neutralEMA = sentiment.neutral() * weight + neutralEMA * (1.0 - weight);
            negativeEMA = sentiment.negative() * weight + negativeEMA * (1.0 - weight);
        }
        return new Sentiment(positiveEMA, neutralEMA, negativeEMA);
    }

    @Nullable
    private Sentiment calculateSupportTeamSentiment(String supportTeam, ImmutableList<SentimentResponse> messageSentiments) {
        ImmutableList<Sentiment> sentiments = messageSentiments.stream()
            .filter(m -> m.message().team().equals(supportTeam))
            .map(SentimentResponse::sentiment)
            .collect(toImmutableList());
        if (sentiments.isEmpty()) {
            return null;
        }
        return new Sentiment(
            sentiments.stream()
                .mapToDouble(Sentiment::positive)
                .average()
                .orElse(0.0),
            sentiments.stream()
                .mapToDouble(Sentiment::neutral)
                .average()
                .orElse(0.0),
            sentiments.stream()
                .mapToDouble(Sentiment::negative)
                .average()
                .orElse(0.0)
        );
    }

    @Nullable
    private Sentiment calculateOthersSentiment(String ticketAuthorId, String supportTeam, ImmutableList<SentimentResponse> messageSentiments) {
        ImmutableList<Sentiment> sentiments = messageSentiments.stream()
            .filter(m -> !m.message().user().equals(ticketAuthorId) && !m.message().team().equals(supportTeam))
            .map(SentimentResponse::sentiment)
            .collect(toImmutableList());
        if (sentiments.isEmpty()) {
            return null;
        }
        return new Sentiment(
            sentiments.stream()
                .mapToDouble(Sentiment::positive)
                .average()
                .orElse(0.0),
            sentiments.stream()
                .mapToDouble(Sentiment::neutral)
                .average()
                .orElse(0.0),
            sentiments.stream()
                .mapToDouble(Sentiment::negative)
                .average()
                .orElse(0.0)
        );
    }
}
