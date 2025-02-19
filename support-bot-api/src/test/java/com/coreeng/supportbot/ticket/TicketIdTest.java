package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.util.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketIdTest {
    public JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testSerialization() {
        TicketId id = new TicketId(1);
        assertEquals("1", jsonMapper.toJsonString(id));
        assertEquals(id,  jsonMapper.fromJsonString("1", TicketId.class));
    }
}