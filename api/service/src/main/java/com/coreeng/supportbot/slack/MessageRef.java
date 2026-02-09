package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.google.common.base.Splitter;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public record MessageRef(
    MessageTs ts,
    @Nullable MessageTs threadTs,
    String channelId
) {
    private static final Splitter slashSplitter = Splitter.on('/');
    private static final Splitter ampSplitter = Splitter.on('&');
    private static final Splitter equalsSplitter = Splitter.on('=').limit(2);

    public MessageRef {
        checkNotNull(ts);
        checkNotNull(channelId);
        // In some cases Slack returns thread_ts == ts, even though it's a top level message
        if (threadTs != null && threadTs.equals(ts)) {
            threadTs = null;
        }
    }

    public MessageRef(MessageTs ts, String channelId) {
        this(ts, null, channelId);
    }

    public static MessageRef from(BlockActionPayload payload) {
        if (payload.getContainer() != null) {
            return new MessageRef(
                MessageTs.of(payload.getContainer().getMessageTs()),
                MessageTs.ofOrNull(payload.getContainer().getThreadTs()),
                payload.getChannel().getId()
            );
        } else if (payload.getMessage() != null) {
            return new MessageRef(
                MessageTs.of(payload.getMessage().getTs()),
                MessageTs.ofOrNull(payload.getMessage().getThreadTs()),
                payload.getChannel().getId()
            );
        }
        throw new IllegalArgumentException("Couldn't build a message ref from payload");
    }

    // Top level link: https://cecg-group.slack.com/archives/C0860GW8BSN/p1736330024278409
    // Link to message in thread: https://cecg-group.slack.com/archives/C0860GW8BSN/p1736330215840179?thread_ts=1736330024.278409&cid=C0860GW8BSN
    public static MessageRef fromPermalink(String permalink) {
        URL url;
        try {
            url = URI.create(permalink).toURL();
        } catch (MalformedURLException e) {
            throw new InvalidPermalink(e);
        }
        String path = url.getPath();
        List<String> parts = slashSplitter.splitToList(path);
        if (parts.size() != 4) {
            throw new InvalidPermalink("Invalid path: " + path);
        }
        String tsMonolith = parts.get(3).substring(1); // strip the 'p'
        if (tsMonolith.length() != 16) {
            throw new InvalidPermalink("Invalid ts: " + tsMonolith);
        }
        MessageTs ts = MessageTs.of(tsMonolith.substring(0, 10) + "." + tsMonolith.substring(10));
        String threadTs = extractThreadTs(url);
        String channelId = parts.get(2);
        return new MessageRef(ts, MessageTs.ofOrNull(threadTs), channelId);
    }

    @Nullable
    private static String extractThreadTs(URL url) {
        if (url.getQuery() == null) {
            return null;
        }
        Iterable<String> queryParts = ampSplitter.split(url.getQuery());
        for (String queryPart : queryParts) {
            List<String> queryParamParts = equalsSplitter.splitToList(queryPart);
            if (queryParamParts.size() == 2 && "thread_ts".equals(queryParamParts.get(0))) {
                return queryParamParts.get(1);
            }
        }
        return null;
    }

    public MessageRef toThreadRef() {
        if (threadTs == null) {
            return this;
        }
        return new MessageRef(threadTs, channelId);
    }

    public MessageTs actualThreadTs() {
        return threadTs != null ? threadTs : ts;
    }

    public boolean isReply() {
        return threadTs != null;
    }
}
