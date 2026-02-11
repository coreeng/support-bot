package com.coreeng.supportbot.config;

import com.coreeng.supportbot.util.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilsConfig {
    @Bean
    public JsonMapper jsonMapper() {
        return new JsonMapper();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return jsonMapper().getObjectMapper();
    }
}
