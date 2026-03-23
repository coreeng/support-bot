package com.coreeng.supportbot.ticket;

public sealed interface StalenessTagTarget {
    String toMention();

    record User(String userId) implements StalenessTagTarget {
        @Override
        public String toMention() {
            return "<@" + userId + ">";
        }
    }

    record Squad(String groupId) implements StalenessTagTarget {
        @Override
        public String toMention() {
            return "<!subteam^" + groupId + ">";
        }
    }
}
