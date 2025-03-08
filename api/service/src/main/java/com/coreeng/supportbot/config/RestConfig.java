package com.coreeng.supportbot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class RestConfig implements WebMvcConfigurer {
    private final List<Formatter<?>> formatters;
    @Override
    public void addFormatters(FormatterRegistry registry) {
        formatters.forEach(registry::addFormatter);
    }
}
