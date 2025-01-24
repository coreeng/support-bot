package com.coreeng.supportbot.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class OpenAPIConfiguration {
    static {
        SpringDocUtils.getConfig()
            .replaceWithClass(ImmutableList.class, List.class)
            .replaceWithClass(ImmutableSet.class, Set.class)
            .replaceWithClass(ImmutableMap.class, Map.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Support Bot API")
                    .version("v0.0.1")
            );
    }
}
