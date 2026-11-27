package com.coreeng.supportbot.docanalysis;

import java.util.Locale;

/**
 * Every tag that wraps data sent to the model, by the analysis or by any source. {@link ModelInput}
 * removes all of them from every part of data, whichever call it is building.
 */
public enum InputTag {
    DOCUMENTATION,
    PAGE,
    AREAS_SUPPLIED,
    AREAS_NOT_SUPPLIED,
    QUESTION,
    THREAD,
    PAGE_INDEX;

    public String tagName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
