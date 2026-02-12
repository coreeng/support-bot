package com.coreeng.supportbot.stats;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import java.time.LocalDate;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

@Getter
@SuperBuilder(toBuilder = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonTypeIdResolver(StatsRequest.TypeIdResolver.class)
public class StatsRequest {
    protected StatsType type;
    protected LocalDate from;
    protected LocalDate to;

    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketTimeline extends StatsRequest {
        private Metric metric;

        {
            type = StatsType.ticketTimeline;
        }

        public enum Metric {
            opened,
            active
        }
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketAmount extends StatsRequest {
        private GroupBy groupBy;

        {
            type = StatsType.ticketsAmount;
        }

        public enum GroupBy {
            status,
            impact
        }
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketGeneral extends StatsRequest {
        {
            type = StatsType.ticketGeneral;
        }
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketSentimentCounts extends StatsRequest {
        {
            type = StatsType.ticketSentimentsCount;
        }
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class Ratings extends StatsRequest {
        {
            type = StatsType.ticketRating;
        }
    }

    public static class TypeIdResolver extends TypeIdResolverBase {

        @Override
        public JavaType typeFromId(DatabindContext context, String id) {
            StatsType type = StatsType.fromLabelOrNull(id);
            Class<?> subClass =
                    switch (type) {
                        case ticketTimeline -> TicketTimeline.class;
                        case ticketsAmount -> TicketAmount.class;
                        case ticketGeneral -> TicketGeneral.class;
                        case ticketSentimentsCount -> TicketSentimentCounts.class;
                        case ticketRating -> Ratings.class;
                        case null -> throw new IllegalArgumentException("Unknown type-id: " + id);
                    };
            return context.constructType(subClass);
        }

        @Override
        public String idFromValue(Object value) {
            if (value instanceof StatsRequest req) {
                return req.type().label();
            }
            throw new IllegalArgumentException("Unexpected value type: " + value.getClass());
        }

        @Override
        public String idFromValueAndType(Object value, Class<?> suggestedType) {
            return idFromValue(value);
        }

        @Override
        public JsonTypeInfo.@Nullable Id getMechanism() {
            return null;
        }
    }
}
