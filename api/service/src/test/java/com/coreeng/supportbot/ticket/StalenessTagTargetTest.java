package com.coreeng.supportbot.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StalenessTagTargetTest {

    @Test
    void userToMentionFormatsSlackUserMention() {
        var target = new StalenessTagTarget.User("U12345");
        assertEquals("<@U12345>", target.toMention());
    }

    @Test
    void squadToMentionFormatsSlackSubteamMention() {
        var target = new StalenessTagTarget.Squad("S08948NBMED");
        assertEquals("<!subteam^S08948NBMED>", target.toMention());
    }
}
