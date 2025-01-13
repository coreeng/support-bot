package com.coreeng.supportbot.homepage;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

@Getter
public enum HomepageOperation {
    previousPage("homepage-previous-page"),
    nextPage("homepage-next-page"),
    filter("homepage-filter"),
    refresh("homepage-refresh");

    public static final Pattern pattern = Pattern.compile("^homepage-.+$");

    private final String actionId;

    HomepageOperation(String actionId) {
        this.actionId = actionId;
    }

    @Nullable
    public static HomepageOperation fromActionIdOrNull(String actionId) {
        for (HomepageOperation value : values()) {
            if (value.actionId.equals(actionId)) {
                return value;
            }
        }
        return null;
    }
}
