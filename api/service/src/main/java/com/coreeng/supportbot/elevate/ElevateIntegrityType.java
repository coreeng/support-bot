package com.coreeng.supportbot.elevate;

public enum ElevateIntegrityType {
    ALL(""),
    ORPHAN_JOURNEY("ORPHAN_JOURNEY"),
    ORPHAN_USER("ORPHAN_USER"),
    MISSING_ASSIGNMENT("MISSING_ASSIGNMENT"),
    CROSS_PRODUCT_ASSIGNMENT("CROSS_PRODUCT_ASSIGNMENT");

    private final String databaseValue;

    ElevateIntegrityType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    String databaseValue() {
        if (this == ALL) {
            throw new IllegalStateException("ALL does not have a database value");
        }
        return databaseValue;
    }
}
