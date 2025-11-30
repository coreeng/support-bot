package com.coreeng.supportbot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogEnricher {
    private final MetricsLoggingProps props;

    public String kv(String key, Object value) {
        if (!props.enabled()) {
            return "";
        }
        return props.prefix() + key + "=" + value;
    }
}
