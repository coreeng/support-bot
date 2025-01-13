package com.coreeng.supportbot.util;

import lombok.RequiredArgsConstructor;

import java.time.*;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class RelativeDateFormatter {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM 'at' HH:mm");
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(" 'at' HH:mm");
    private final ZoneId offset;

    public String format(Instant instant) {
        return format(instant, offset);
    }

    public String format(Instant instant, ZoneId offset) {
        ZonedDateTime dateTime = instant.atZone(offset);
        LocalDate date = dateTime.toLocalDate();

        ZonedDateTime now = ZonedDateTime.now(offset);
        LocalDate today = now.toLocalDate();

        if (date.equals(today)) {
            return "Today" + timeFormatter.format(dateTime);
        } else if (date.equals(today.minusDays(1))) {
            return "Yesterday" + timeFormatter.format(dateTime);
        } else {
            return dateTimeFormatter.format(dateTime);
        }
    }

}
