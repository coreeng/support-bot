package com.coreeng.supportbot.elevate;

public enum ElevateDirection {
    ASC,
    DESC;

    String sql() {
        return name();
    }
}
