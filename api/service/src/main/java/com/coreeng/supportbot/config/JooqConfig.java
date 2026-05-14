package com.coreeng.supportbot.config;

import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    @Bean
    public DefaultConfigurationCustomizer schemaRenderMapping(@Value("${app.db.schema}") String schema) {
        return cfg -> cfg.settings()
                .withRenderMapping(new RenderMapping()
                        .withSchemata(new MappedSchema()
                                // "public" here must match jOOQ codegen's inputSchema in build.gradle.kts — if that
                                // changes, change it here too.
                                .withInput("public")
                                .withOutput(schema)));
    }
}
