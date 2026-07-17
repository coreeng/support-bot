package com.coreeng.supportbot.elevate;

public final class ElevateResourceNotFoundException extends RuntimeException {
    private final String resourceType;

    public ElevateResourceNotFoundException(String resourceType) {
        super("Elevate " + resourceType + " not found");
        this.resourceType = resourceType;
    }

    public String resourceType() {
        return resourceType;
    }
}
