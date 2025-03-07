package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.util.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TicketEscalateInputTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testSerialization() {
        TicketEscalateInput input = new TicketEscalateInput(new TicketId(1));
        assertEquals("""
            {"ticketId":1}""", jsonMapper.toJsonString(input));
        assertEquals(input, jsonMapper.fromJsonString("""
            {"ticketId":1}""", TicketEscalateInput.class));
    }
}