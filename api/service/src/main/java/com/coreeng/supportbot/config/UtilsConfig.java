package com.coreeng.supportbot.config;

import com.coreeng.supportbot.util.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilsConfig {
    @Bean
    public JsonMapper jsonMapper() {
        return new JsonMapper();
    }

    @Bean
    public tools.jackson.databind.json.JsonMapper objectMapper() {
        return jsonMapper().getObjectMapper();
    }
}
