package com.coreeng.supportbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

@Configuration
public class UtilsConfig {
    public final static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM 'at' HH:mm");

    @Bean
    public DateTimeFormatter dateFormat() {
        return dateFormatter;
    }
}
