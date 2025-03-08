package com.coreeng.supportbot.config;

import com.coreeng.supportbot.util.JsonMapper;
import com.coreeng.supportbot.util.RelativeDateFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class UtilsConfig {
    @Bean
    public ZoneId timezone(@Value("${application.timezone}") String timezone) {
        return ZoneId.of(timezone);
    }

    @Bean
    public RelativeDateFormatter dateFormatter(ZoneId zoneId) {
        return new RelativeDateFormatter(zoneId);
    }

    @Bean
    public JsonMapper jsonMapper() {
        return new JsonMapper();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return jsonMapper().getObjectMapper();
    }
}
