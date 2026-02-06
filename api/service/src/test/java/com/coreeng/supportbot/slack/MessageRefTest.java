package com.coreeng.supportbot.slack;

import org.jspecify.annotations.Nullable;
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

    private static String createPermalink(String channelId, String tsLeft, String tsRight, @Nullable String threadTs) {
            return createPermalink(channelId, tsLeft, tsRight, threadTs, null);
    }

    private static String createPermalink(
        String channelId,
        String tsLeft,
        String tsRight,
        @Nullable String threadTs,
        @Nullable String cid
    ) {
        return "https://cecg-group.slack.com/archives/" + channelId
            + "/p" + tsLeft + tsRight
            + (threadTs != null ? "?thread_ts=" + threadTs + "&cid=" + channelId : "")
            + (threadTs != null && cid != null ? "&cid=" + cid : "");
    }

    @Test
    public void whenThreadTsEqualsTs_thenThreadTsIsNormalizedToNull() {
        // Slack sometimes returns thread_ts == ts for top-level messages
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, ts, channelId);

        assertNull(messageRef.threadTs(), "threadTs should be null when it equals ts");
        assertFalse(messageRef.isReply(), "should not be considered a reply");
        assertEquals(ts, messageRef.actualThreadTs(), "actualThreadTs should return ts when threadTs is null");
    }

    @Test
    public void whenThreadTsIsDifferentFromTs_thenThreadTsIsKept() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageTs threadTs = MessageTs.of("1234567890.111111");
        MessageRef messageRef = new MessageRef(ts, threadTs, channelId);

        assertEquals(threadTs, messageRef.threadTs(), "threadTs should be kept when different from ts");
        assertTrue(messageRef.isReply(), "should be considered a reply");
        assertEquals(threadTs, messageRef.actualThreadTs(), "actualThreadTs should return threadTs");
    }

    @Test
    public void whenThreadTsIsNull_thenThreadTsRemainsNull() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, null, channelId);

        assertNull(messageRef.threadTs(), "threadTs should remain null");
        assertFalse(messageRef.isReply(), "should not be considered a reply");
        assertEquals(ts, messageRef.actualThreadTs(), "actualThreadTs should return ts when threadTs is null");
    }

    @Test
    public void whenCreatedWithTwoArgConstructor_thenThreadTsIsNull() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, channelId);

        assertNull(messageRef.threadTs(), "threadTs should be null when using two-arg constructor");
        assertFalse(messageRef.isReply(), "should not be considered a reply");
    }

    @Test
    public void whenIsReply_toThreadRef_returnsRefToParentThread() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageTs threadTs = MessageTs.of("1234567890.111111");
        MessageRef messageRef = new MessageRef(ts, threadTs, channelId);

        MessageRef threadRef = messageRef.toThreadRef();

        assertEquals(threadTs, threadRef.ts(), "toThreadRef should use threadTs as ts");
        assertNull(threadRef.threadTs(), "toThreadRef result should have null threadTs");
        assertEquals(channelId, threadRef.channelId());
    }

    @Test
    public void whenNotReply_toThreadRef_returnsSameRef() {
        MessageTs ts = MessageTs.of("1234567890.123456");
        MessageRef messageRef = new MessageRef(ts, channelId);

        MessageRef threadRef = messageRef.toThreadRef();

        assertSame(messageRef, threadRef, "toThreadRef should return same ref when not a reply");
    }
}
