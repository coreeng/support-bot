package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportLeadershipTeamProps;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SupportLeadershipTeamProps.class)
@RequiredArgsConstructor
public class SupportLeadershipTeamConfig {

}