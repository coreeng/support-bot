package com.coreeng.supportbot.testkit;


import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestKit {
    private final SlackWiremock slackWiremock;
    private final SupportBotSlackClient supportBotSlackClient;
    private final SupportBotClient supportBotClient;
    private final Config config;

    /**
     * Creates a TestKit instance with all dependencies initialized.
     * The SlackWiremock is created but NOT started - caller must call
     * {@code testKit.slack().wiremock().start()} to start it.
     *
     * @param config the configuration to use
     * @return a new TestKit instance
     */
    public static TestKit create(Config config) {
        SlackWiremock slackWiremock = new SlackWiremock(config.mocks().slack());
        SupportBotClient supportBotClient = new SupportBotClient(config.supportBot().baseUrl(), slackWiremock);
        SupportBotSlackClient supportBotSlackClient = new SupportBotSlackClient(config, slackWiremock);
        return new TestKit(slackWiremock, supportBotSlackClient, supportBotClient, config);
    }

    public RoledTestKit as(UserRole role) {
        return new RoledTestKit(role);
    }

    /**
     * Returns Slack-related resources for this TestKit.
     */
    public SlackResources slack() {
        return new SlackResources();
    }

    /**
     * Provides access to Slack-related test resources.
     */
    public class SlackResources {
        /**
         * Returns the SlackWiremock instance for this TestKit.
         * Use this to start/stop the server, set up stubs, and verify requests.
         */
        public SlackWiremock wiremock() {
            return slackWiremock;
        }
    }

    /**
     * Returns the SupportBotClient for making API calls to the support bot service.
     */
    public SupportBotClient supportBotClient() {
        return supportBotClient;
    }

    /**
     * Returns the configuration used by this TestKit.
     */
    public Config config() {
        return config;
    }

    @RequiredArgsConstructor
    public class RoledTestKit {
        private final static String workflowBotId = "B0123456789";

        private final UserRole role;

        public SlackTestKit slack() {
            return new SlackTestKit(this, supportBotSlackClient);
        }

        public TicketTestKit ticket() {
            return new TicketTestKit(this, supportBotClient, slackWiremock, config);
        }

        public String userId() {
            return switch (role) {
                case tenant -> config.nonSupportUsers().getFirst().slackUserId();
                case support -> config.supportUsers().getFirst().slackUserId();
                case supportBot -> config.mocks().slack().supportBotUserId();
                case workflow -> null;
            };
        }

        public String botId() {
            return switch (role) {
                case tenant, support -> null;
                case supportBot -> config.mocks().slack().supportBotId();
                case workflow -> workflowBotId;
            };
        }

        public Config.@NonNull User user() {
            return switch (role) {
                case tenant -> config.nonSupportUsers().getFirst();
                case support -> config.supportUsers().getFirst();
                case supportBot -> new Config.User(
                    "support.bot@cecg.io",
                    config.mocks().slack().supportBotUserId(),
                    config.mocks().slack().supportBotId()
                );
                case workflow -> new Config.User(null, null, workflowBotId);
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
