package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class ElevateErrorSanitizer {
    private static final int MAX_ERROR_LENGTH = 500;
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLIENT_SECRET =
            Pattern.compile("(?i)(client[_-]?secret\\s*[=:]\\s*)[^\\s,;]+", Pattern.CASE_INSENSITIVE);

    private final ElevateProps props;

    public String sanitize(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        message = BEARER_TOKEN.matcher(message).replaceAll("Bearer <redacted>");
        message = CLIENT_SECRET.matcher(message).replaceAll("$1<redacted>");
        message = replaceConfiguredValue(message, props.clientSecret());
        message = replaceConfiguredValue(message, props.clientId());
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private static String replaceConfiguredValue(String message, String configuredValue) {
        return configuredValue.isEmpty() ? message : message.replace(configuredValue, "<redacted>");
    }
}
