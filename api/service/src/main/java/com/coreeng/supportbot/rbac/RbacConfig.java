package com.coreeng.supportbot.rbac;

import com.coreeng.supportbot.teams.SupportTeamService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RbacProps.class)
public class RbacConfig {
    @Bean
    public RbacService rbacService(RbacProps rbacProps, SupportTeamService supportTeamService) {
        return new RbacService(rbacProps, supportTeamService);
    }
}
