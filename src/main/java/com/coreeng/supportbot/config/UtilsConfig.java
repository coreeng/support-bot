package com.coreeng.supportbot.config;

import com.coreeng.supportbot.util.JsonMapper;
import com.coreeng.supportbot.util.RelativeDateFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class UtilsConfig {
    @Bean
    public RelativeDateFormatter dateFormatter(@Value("${application.timezone}") String timezone) {
        return new RelativeDateFormatter(ZoneId.of(timezone));
    }

    @Bean
    public JsonMapper jsonMapper() {
        return new JsonMapper();
    }
}
