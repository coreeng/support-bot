package com.coreeng.supportbot.ticket;

import static org.junit.jupiter.api.Assertions.*;

import com.coreeng.supportbot.util.JsonMapper;
import org.junit.jupiter.api.Test;

class TicketIdTest {
    public JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testSerialization() {
        TicketId id = new TicketId(1);
        assertEquals("1", jsonMapper.toJsonString(id));
        assertEquals(id, jsonMapper.fromJsonString("1", TicketId.class));
    }
}
