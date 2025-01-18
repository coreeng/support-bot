package com.coreeng.supportbot.slack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageRefTest {
    private static final String channelId = "C0860GW8BSN";
    private static final String tsLeft = "1111111111";
    private static final String tsRight = "222222";
    private static final String threadTs = "3333333333.444444";

    @Test
    public void testFromValidTopLevelPermalink() {
        MessageRef messageRef = MessageRef.fromPermalink(createPermalink(channelId, tsLeft, tsRight, null));
        assertEquals(channelId, messageRef.channelId());
        assertEquals(MessageTs.of(tsLeft + "." + tsRight), messageRef.ts());
        assertNull(messageRef.threadTs());
    }


    @Test
    public void testFromValidThreadMessagePermalink() {
        MessageRef messageRef = MessageRef.fromPermalink(createPermalink(channelId, tsLeft, tsRight, threadTs));
        assertEquals(channelId, messageRef.channelId());
        assertEquals(MessageTs.of(tsLeft + "." + tsRight), messageRef.ts());
        assertEquals(MessageTs.of(threadTs), messageRef.threadTs());
    }

    // In some cases attaches additional parameter to represent channel ID to open on a side view
    @Test
    public void testFromValidThreadMessagePermalinkWithCID() {
        MessageRef messageRef = MessageRef.fromPermalink(createPermalink(channelId, tsLeft, tsRight, threadTs));
        assertEquals(channelId, messageRef.channelId());
        assertEquals(MessageTs.of(tsLeft + "." + tsRight), messageRef.ts());
        assertEquals(MessageTs.of(threadTs), messageRef.threadTs());
    }

    private static String createPermalink(String channelId, String tsLeft, String tsRight, String threadTs) {
            return createPermalink(channelId, tsLeft, tsRight, threadTs, null);
    }

    private static String createPermalink(String channelId, String tsLeft, String tsRight, String threadTs, String cid) {
        return "https://cecg-group.slack.com/archives/" + channelId
            + "/p" + tsLeft + tsRight
            + (threadTs != null ? "?thread_ts=" + threadTs + "&cid=" + channelId : "")
            + (threadTs != null && cid != null ? "&cid=" + cid : "");
    }
}