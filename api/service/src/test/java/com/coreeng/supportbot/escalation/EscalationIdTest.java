package com.coreeng.supportbot.escalation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coreeng.supportbot.util.JsonMapper;
import org.junit.jupiter.api.Test;

public class EscalationIdTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testSerialization() {
        EscalationId escalationId = new EscalationId(1);
        assertEquals("1", jsonMapper.toJsonString(escalationId));
        assertEquals(escalationId, jsonMapper.fromJsonString("1", EscalationId.class));
    }
}
