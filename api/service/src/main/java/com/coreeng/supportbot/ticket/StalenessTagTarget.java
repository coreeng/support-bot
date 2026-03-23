package com.coreeng.supportbot.ticket;

import static com.google.common.base.Preconditions.checkArgument;

public sealed interface StalenessTagTarget {
    String toMention();

    record User(String userId) implements StalenessTagTarget {
        public User {
            checkArgument(!userId.isBlank(), "userId must not be blank");
        }

        @Override
        public String toMention() {
            return "<@" + userId + ">";
        }
    }

    record Squad(String groupId) implements StalenessTagTarget {
        public Squad {
            checkArgument(!groupId.isBlank(), "groupId must not be blank");
        }

        @Override
        public String toMention() {
            return "<!subteam^" + groupId + ">";
        }
    }
}
