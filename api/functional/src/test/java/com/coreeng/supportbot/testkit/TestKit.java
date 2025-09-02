package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.Config;
import com.coreeng.supportbot.wiremock.WiremockManager;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestKit {
    private final WiremockManager wiremockManager;
    private final SupportBotSlackClient supportBotSlackClient;
    private final SupportBotClient supportBotClient;
    private final Config config;

    public RoledTestKit as(UserRole role) {
        return new RoledTestKit(role);
    }

    @RequiredArgsConstructor
    public class RoledTestKit {
        private final UserRole role;

        public SlackTestKit slack() {
            return new SlackTestKit(this, wiremockManager.slackWiremock, supportBotSlackClient);
        }
        
        public TicketTestKit ticket() {
            return new TicketTestKit(this, supportBotClient, wiremockManager.slackWiremock, config);
        }
        
        public String userId() {
            return switch (role) {
                case tenant -> config.nonSupportUsers().getFirst().slackUserId();
                case support -> config.supportUsers().getFirst().slackUserId();
                case supportBot -> config.mocks().slack().botId();
            };
        }

        public Config.@NonNull User user() {
            return switch (role) {
                case tenant -> config.nonSupportUsers().getFirst();
                case support -> config.supportUsers().getFirst();
                case supportBot -> new Config.User(config.mocks().slack().botId(), "support.bot@cecg.io");
            };
        }

        public String teamId() {
            return config.mocks().slack().teamId();
        }

        public String channelId() {
            return config.mocks().slack().supportChannelId();
        }
    }
}
