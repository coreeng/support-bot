package com.coreeng.supportbot.slack;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.model.User;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Formats Slack's internal text format to human-readable text.
 * Resolves user, channel, and group mentions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlackTextFormatter {
    private final SlackClient slackClient;

    private static final Pattern USER_MENTION = Pattern.compile("<@([UW][A-Z0-9]+)>");
    private static final Pattern SUBTEAM_MENTION = Pattern.compile("<!subteam\\^([A-Z0-9]+)(?:\\|@([^>]*))?>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#([C][A-Z0-9]+)(?:\\|([^>]*))?>");
    private static final Pattern LINK = Pattern.compile("<(https?://[^|>]+)(?:\\|([^>]*))?>");
    // Special mentions we actively support
    private static final Pattern SPECIAL_MENTION = Pattern.compile("<!(here|channel|everyone)>");

    @Nullable public String format(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;
        result = replace(result, USER_MENTION, this::formatUserMention);
        result = replace(result, SUBTEAM_MENTION, this::formatSubteamMention);
        result = replace(result, CHANNEL_MENTION, this::formatChannelMention);
        result = replace(result, LINK, this::formatLink);
        result = replace(result, SPECIAL_MENTION, m -> "@" + m.group(1));
        return result;
    }

    private String replace(String text, Pattern pattern, Function<Matcher, String> formatter) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(formatter.apply(matcher)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String formatUserMention(Matcher matcher) {
        String userId = matcher.group(1);
        try {
            var user = slackClient.getUserById(SlackId.user(userId));
            return "@" + resolveUserDisplay(user, userId);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve user {}: {}", userId, e.getMessage());
            }
            return "@" + userId;
        }
    }

    private String formatSubteamMention(Matcher matcher) {
        String subteamId = matcher.group(1);
        String handle = matcher.group(2);

        if (handle != null && !handle.isBlank()) {
            return "@" + handle;
        }

        try {
            String groupName = slackClient.getGroupName(SlackId.group(subteamId));
            return "@" + (groupName != null ? groupName : "group-" + subteamId);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve subteam {}: {}", subteamId, e.getMessage());
            }
            return "@group-" + subteamId;
        }
    }

    private String formatChannelMention(Matcher matcher) {
        String channelId = matcher.group(1);
        String channelName = matcher.group(2);

        if (channelName != null && !channelName.isBlank()) {
            return "#" + channelName;
        }

        try {
            String name = slackClient.getChannelName(channelId);
            return "#" + (name != null && !name.isBlank() ? name : channelId);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to resolve channel {}: {}", channelId, e.getMessage());
            }
            return "#" + channelId;
        }
    }

    private String formatLink(Matcher matcher) {
        String url = matcher.group(1);
        String text = matcher.group(2);
        return text != null ? text + " (" + url + ")" : url;
    }

    private String resolveUserDisplay(User user, String fallbackId) {
        if (user == null) {
            return fallbackId;
        }
        var profile = user.getProfile();
        if (profile != null && profile.getDisplayName() != null) {
            return profile.getDisplayName();
        }
        if (profile != null && profile.getRealName() != null) {
            return profile.getRealName();
        }
        if (user.getName() != null) {
            return user.getName();
        }
        return fallbackId;
    }
}
