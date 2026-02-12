package com.coreeng.supportbot.slack;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MessageRefTest {
    private static final String CHANNEL_ID = "C0860GW8BSN";
    private static final String TS_LEFT = "1111111111";
    private static final String TS_RIGHT = "222222";
    private static final String THREAD_TS = "3333333333.444444";

    @Test
    public void testFromValidTopLevelPermalink() {
        MessageRef messageRef = MessageRef.fromPermalink(createPermalink(CHANNEL_ID, TS_LEFT, TS_RIGHT, null));
        assertEquals(CHANNEL_ID, messageRef.channelId());
        assertEquals(MessageTs.of(TS_LEFT + "." + TS_RIGHT), messageRef.ts());
        assertNull(messageRef.threadTs());
    }

    @Test
    public void testFromValidThreadMessagePermalink() {
        MessageRef messageRef = MessageRef.fromPermalink(createPermalink(CHANNEL_ID, TS_LEFT, TS_RIGHT, THREAD_TS));
        assertEquals(CHANNEL_ID, messageRef.channelId());
        assertEquals(MessageTs.of(TS_LEFT + "." + TS_RIGHT), messageRef.ts());
        assertEquals(MessageTs.of(THREAD_TS), messageRef.threadTs());
    }

    // In some cases attaches additional parameter to represent channel ID to open on a side view
    @Test
    public void testFromValidThreadMessagePermalinkWithCID() {
        MessageRef messageRef = MessageRef.fromPermalink(createPermalink(CHANNEL_ID, TS_LEFT, TS_RIGHT, THREAD_TS));
        assertEquals(CHANNEL_ID, messageRef.channelId());
        assertEquals(MessageTs.of(TS_LEFT + "." + TS_RIGHT), messageRef.ts());
        assertEquals(MessageTs.of(THREAD_TS), messageRef.threadTs());
    }

    private static String createPermalink(String channel, String left, String right, @Nullable String thread) {
        return createPermalink(channel, left, right, thread, null);
    }

    private static String createPermalink(
            String channel, String left, String right, @Nullable String thread, @Nullable String cid) {
        return "https://cecg-group.slack.com/archives/" + channel
                + "/p" + left + right
                + (thread != null ? "?thread_ts=" + thread + "&cid=" + channel : "")
                + (thread != null && cid != null ? "&cid=" + cid : "");
    }

    @Test
    public void whenThreadTsEqualsTs_thenThreadTsIsNormalizedToNull() {
        // Slack sometimes returns thread_ts == ts for top-level messages
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, ts, CHANNEL_ID);

        assertNull(messageRef.threadTs(), "threadTs should be null when it equals ts");
        assertFalse(messageRef.isReply(), "should not be considered a reply");
        assertEquals(ts, messageRef.actualThreadTs(), "actualThreadTs should return ts when threadTs is null");
    }

    @Test
    public void whenThreadTsIsDifferentFromTs_thenThreadTsIsKept() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageTs localThreadTs = MessageTs.of("1234567890.111111");
        MessageRef messageRef = new MessageRef(ts, localThreadTs, CHANNEL_ID);

        assertEquals(localThreadTs, messageRef.threadTs(), "threadTs should be kept when different from ts");
        assertTrue(messageRef.isReply(), "should be considered a reply");
        assertEquals(localThreadTs, messageRef.actualThreadTs(), "actualThreadTs should return threadTs");
    }

    @Test
    public void whenThreadTsIsNull_thenThreadTsRemainsNull() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, null, CHANNEL_ID);

        assertNull(messageRef.threadTs(), "threadTs should remain null");
        assertFalse(messageRef.isReply(), "should not be considered a reply");
        assertEquals(ts, messageRef.actualThreadTs(), "actualThreadTs should return ts when threadTs is null");
    }

    @Test
    public void whenCreatedWithTwoArgConstructor_thenThreadTsIsNull() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, CHANNEL_ID);

        assertNull(messageRef.threadTs(), "threadTs should be null when using two-arg constructor");
        assertFalse(messageRef.isReply(), "should not be considered a reply");
    }

    @Test
    public void whenIsReply_toThreadRef_returnsRefToParentThread() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageTs localThreadTs = MessageTs.of("1234567890.111111");
        MessageRef messageRef = new MessageRef(ts, localThreadTs, CHANNEL_ID);

        MessageRef threadRef = messageRef.toThreadRef();

        assertEquals(localThreadTs, threadRef.ts(), "toThreadRef should use threadTs as ts");
        assertNull(threadRef.threadTs(), "toThreadRef result should have null threadTs");
        assertEquals(CHANNEL_ID, threadRef.channelId());
    }

    @Test
    public void whenNotReply_toThreadRef_returnsSameRef() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, CHANNEL_ID);

        MessageRef threadRef = messageRef.toThreadRef();

        assertSame(messageRef, threadRef, "toThreadRef should return same ref when not a reply");
    }
}
