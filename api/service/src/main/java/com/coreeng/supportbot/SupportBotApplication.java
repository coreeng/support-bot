package com.coreeng.supportbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SuppressWarnings({"removal"})
@SpringBootApplication(
        exclude = {
            org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
        })
@EnableScheduling
@ConfigurationPropertiesScan("com.coreeng.supportbot.config")
public class SupportBotApplication {
    public static void main(String[] args) {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");
        SpringApplication.run(SupportBotApplication.class, args);
    }
}
