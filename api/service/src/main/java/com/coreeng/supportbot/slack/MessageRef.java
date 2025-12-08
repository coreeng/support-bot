package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;

public record MessageRef(
    MessageTs ts,
    MessageTs threadTs,
    String channelId
) {
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
        String[] parts = path.split("/");
        if (parts.length != 4) {
            throw new InvalidPermalink("Invalid path: " + path);
        }
        String tsMonolith = parts[3].substring(1); // strip the 'p'
        if (tsMonolith.length() != 16) {
            throw new InvalidPermalink("Invalid ts: " + tsMonolith);
        }
        MessageTs ts = MessageTs.of(tsMonolith.substring(0, 10) + "." + tsMonolith.substring(10));
        String threadTs = extractThreadTs(url);
        String channelId = parts[2];
        return new MessageRef(ts, MessageTs.ofOrNull(threadTs), channelId);
    }

    private static String extractThreadTs(URL url) {
        if (url.getQuery() == null) {
            return null;
        }
        String[] queryParts = url.getQuery().split("&");
        for (String queryPart : queryParts) {
            String[] queryParamParts = queryPart.split("=");
            if (queryParamParts.length == 2 && "thread_ts".equals(queryParamParts[0])) {
                return queryParamParts[1];
            }
        }
        return null;
    }

    public MessageRef toThreadRef() {
        return isReply()
            ? new MessageRef(threadTs, channelId)
            : this;
    }

    public MessageTs actualThreadTs() {
        return threadTs != null ? threadTs : ts;
    }

    public boolean isReply() {
        return threadTs != null;
    }
}
