package com.coreeng.supportbot.troubleshooting;

public final class Actors {
    public static final String SYSTEM = "system";

    private Actors() {}

    public static String slack(String userId) {
        return "slack:" + userId;
    }
}
