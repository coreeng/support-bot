package com.coreeng.supportbot.ticket.rest;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record TicketEscalationCreateRequest(@Nullable String team, List<String> tags) {}
