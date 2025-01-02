package com.coreeng.supportbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.coreeng.supportbot.config")
public class SupportBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupportBotApplication.class, args);
    }
}
