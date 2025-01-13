package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.util.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomepageViewTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testStateSerialization() {
        HomepageView.State state = HomepageView.State.getDefault();
        String serialized = jsonMapper.toJsonString(state);
        HomepageView.State deserialized = jsonMapper.fromJsonString(serialized, HomepageView.State.class);
        assertEquals(state, deserialized);
    }
}